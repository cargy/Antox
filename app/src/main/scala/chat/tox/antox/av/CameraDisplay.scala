package chat.tox.antox.av

import android.app.Activity
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import chat.tox.antox.utils.{UiUtils, AntoxLog}
import rx.lang.scala.JavaConversions._
import rx.lang.scala.{Observable, Subject}
import scala.collection.JavaConversions._

class CameraDisplay(activity: Activity, previewView: TextureView) extends SurfaceTextureListener {

  private var maybeCamera: Option[AntoxCamera] = None

  private val frameSubject: Subject[YuvVideoFrame] = Subject[YuvVideoFrame]()
  val frameObservable: Observable[YuvVideoFrame] = frameSubject.asJavaObservable

  private var active: Boolean = false

  def start(camera: AntoxCamera): Unit = {
    previewView.setSurfaceTextureListener(this)
    maybeCamera = Some(camera)

    active = true
  }

  def create(camera: AntoxCamera, texture: SurfaceTexture): Unit = {
    setParameters(camera)
    adjustToOptimalDisplaySize(camera)

    val previewCallback = new PreviewCallback {
      override def onPreviewFrame(data: Array[Byte], camera: Camera): Unit = {
        //AntoxLog.debug("got a camera preview frame")
        // frameSubject.onNext()
      }
    }

    //camera.addCallbackBuffer()
    camera.setPreviewCallback(previewCallback)
    recreate(camera, texture)
  }

  def recreate(camera: AntoxCamera, texture: SurfaceTexture): Unit = {
    try {
      camera.setPreviewTexture(texture)
      camera.startPreview()
    } catch {
      case e: Exception =>
        AntoxLog.error("Error starting camera preview.")
    }
  }

  def setParameters(camera: AntoxCamera): Unit = {
    val parameters = camera.getParameters
    parameters.setRecordingHint(true)
    camera.setParameters(parameters)
  }

  def adjustToOptimalDisplaySize(camera: AntoxCamera): Unit = {
    val validPreviewSizes = camera.getParameters.getSupportedPreviewSizes

    AntoxLog.debug(s"Valid preview sizes are ${validPreviewSizes.map(size => s"${size.width}, ${size.height}")}")

    val orientation = activity.getResources.getConfiguration.orientation
    val vertical = orientation == Configuration.ORIENTATION_PORTRAIT

    val desiredWidth = previewView.getWidth
    val desiredHeight = previewView.getHeight

    //val leeway = 1.4 // percentage

    //lower is better
    def closenessScore(size: Camera#Size) = Math.abs(size.width - desiredWidth) + Math.abs(size.height - desiredHeight)

    val closestSize = validPreviewSizes.sortBy(closenessScore).head

    println(s"chosen size was ${closestSize.width}, ${closestSize.height}")
    println(s"view size is ${previewView.getWidth}, ${previewView.getHeight}")

    /* if (false && (closestSize.width < desiredWidth * leeway || closestSize.width > desiredWidth * leeway
      || closestSize.height < desiredHeight * leeway || closestSize.height > desiredHeight * leeway)) {
      // couldn't find a nice resolution, give up and scale the video
      println("couldn't find a nice resolution")
      UiUtils.adjustAspectRatio(activity, previewView, closestSize.width, closestSize.height)
    } else { */
      // could find a nice resolution (yay), scale the textureview
    println("could find a nice resolution")
    val layoutParams = previewView.getLayoutParams
    layoutParams.width = if (vertical) closestSize.height else closestSize.width
    layoutParams.height = if (vertical) closestSize.width else closestSize.height
    previewView.setLayoutParams(layoutParams)
    //}

    val newParameters = camera.getParameters
    newParameters.setPreviewSize(closestSize.width, closestSize.height)

    camera.setParameters(newParameters)
  }

  override def onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int): Unit = {
    if (!active) return

    maybeCamera.foreach(create(_, surface))
  }

  override def onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int): Unit = {
    if (!active) return

    maybeCamera.foreach(camera => {
      try {
        camera.stopPreview()
      } catch {
        case e: Exception =>
          e.printStackTrace()
      }

      recreate(camera, surface)
    })
  }

  //do nothing, we don't care
  override def onSurfaceTextureUpdated(surface: SurfaceTexture): Unit = {}

  override def onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

  def stop(): Unit = {
    active = false

    maybeCamera.foreach(camera => {
      camera.stopPreview()
      camera.setPreviewCallback(null)
      camera.release()
    })
  }
}