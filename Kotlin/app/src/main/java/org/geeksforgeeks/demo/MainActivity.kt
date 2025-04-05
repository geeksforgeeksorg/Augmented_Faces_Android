package org.geeksforgeeks.demo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Sceneform
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.ArFrontFacingFragment
import com.google.ar.sceneform.ux.AugmentedFaceNode
import java.util.concurrent.CompletableFuture

// MainActivity: The entry point of the application.
class MainActivity : AppCompatActivity() {
    // A set to store the asynchronous model and texture loaders.
    private val loaders: MutableSet<CompletableFuture<*>> = HashSet()

    // AR Fragment that handles face tracking.
    private var arFragment: ArFrontFacingFragment? = null
    private var arSceneView: ArSceneView? = null

    // Variables for face texture and 3D model.
    private var faceTexture: Texture? = null
    private var faceModel: ModelRenderable? = null

    // Map to track detected faces and their corresponding AugmentedFaceNode.
    private val facesNodes = HashMap<AugmentedFace, AugmentedFaceNode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Listen for fragment attachment events
        supportFragmentManager.addFragmentOnAttachListener { fragmentManager: FragmentManager, fragment: Fragment ->
            this.onAttachFragment(fragmentManager, fragment)
        }

        // Check if this is a new instance of the activity
        if (savedInstanceState == null) {
            // Verify if the device supports Sceneform
            if (Sceneform.isSupported(this)) {
                // Add AR fragment dynamically
                supportFragmentManager.beginTransaction()
                    .add(R.id.arFragment, ArFrontFacingFragment::class.java, null)
                    .commit()
            }
        }

        // Load 3D model and textures
        loadModels()
        loadTextures()
    }

    // Called when a fragment is attached to the activity.
    private fun onAttachFragment(fragmentManager: FragmentManager, fragment: Fragment) {
        if (fragment.id == R.id.arFragment) {
            arFragment = fragment as ArFrontFacingFragment
            // Set a listener for when the AR scene view is created.
            arFragment!!.setOnViewCreatedListener { arSceneView: ArSceneView ->
                this.onViewCreated(arSceneView)
            }
        }
    }

    // Called when the AR scene view is ready.
    private fun onViewCreated(arSceneView: ArSceneView) {
        this.arSceneView = arSceneView

        // Set the camera stream to render first to ensure proper occlusion of 3D objects.
        arSceneView.setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST)

        // Listen for updates on detected faces.
        arFragment!!.setOnAugmentedFaceUpdateListener { augmentedFace: AugmentedFace ->
            this.onAugmentedFaceTrackingUpdate(augmentedFace)
        }
    }

    // Clean up resources when the activity is destroyed.
    override fun onDestroy() {
        super.onDestroy()

        // Cancel all incomplete asynchronous tasks.
        for (loader in loaders) {
            if (!loader.isDone) {
                loader.cancel(true)
            }
        }
    }

    // Load 3D model asynchronously.
    private fun loadModels() {
        loaders.add(ModelRenderable.builder()
            .setSource(this, R.raw.fox) // Load the 3D model file (fox.glb or fox.gltf)
            .setIsFilamentGltf(true) // Set the format as Filament GLTF
            .build()
            .thenAccept { model: ModelRenderable? -> faceModel = model } // Store the loaded model
            .exceptionally {
                // Show error message if loading fails
                Toast.makeText(this, "Unable to load render-able", Toast.LENGTH_LONG).show()
                null
            })
    }

    // Load texture asynchronously.
    private fun loadTextures() {
        loaders.add(
            Texture.builder()
                .setSource(this, R.raw.freckles) // Load texture file (freckles.png)
                .setUsage(Texture.Usage.COLOR_MAP) // Define usage as color mapping
                .build()
                .thenAccept { texture: Texture? -> faceTexture = texture } // Store the loaded texture
                .exceptionally {
                    // Show error message if loading fails
                    Toast.makeText(this, "Unable to load texture", Toast.LENGTH_LONG).show()
                    null
                })
    }

    // Handles face tracking updates.
    private fun onAugmentedFaceTrackingUpdate(augmentedFace: AugmentedFace) {
        // If model or texture is not loaded yet, do nothing.
        if (faceModel == null || faceTexture == null) {
            return
        }

        // Check if the face is already being tracked.
        val existingFaceNode = facesNodes[augmentedFace]

        // Handle different tracking states of the face.
        when (augmentedFace.trackingState) {
            TrackingState.TRACKING ->
                if (existingFaceNode == null) { // If the face is newly detected
                    val faceNode = AugmentedFaceNode(augmentedFace)

                    // Attach the 3D model to the face.
                    val modelInstance = faceNode.setFaceRegionsRenderable(faceModel)
                    modelInstance.isShadowCaster = false // Prevent the model from casting shadows.
                    modelInstance.isShadowReceiver = true // Allow the model to receive shadows.

                    // Apply texture to the face mesh.
                    faceNode.faceMeshTexture = faceTexture

                    // Add face node to the scene.
                    arSceneView!!.scene.addChild(faceNode)

                    // Store the node in the map.
                    facesNodes[augmentedFace] = faceNode
                }

            TrackingState.STOPPED -> {
                // If face tracking stopped, remove it from the scene.
                if (existingFaceNode != null) {
                    arSceneView!!.scene.removeChild(existingFaceNode)
                }
                facesNodes.remove(augmentedFace)
            }

            TrackingState.PAUSED -> {
                // If face tracking is paused, remove it from the scene.
                if (existingFaceNode != null) {
                    arSceneView!!.scene.removeChild(existingFaceNode)
                }
                facesNodes.remove(augmentedFace)
            }
        }
    }
}