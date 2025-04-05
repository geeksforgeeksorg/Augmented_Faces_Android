package org.geeksforgeeks.demo;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

// Required ARCore imports
import com.google.ar.core.AugmentedFace;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.ArFrontFacingFragment;
import com.google.ar.sceneform.ux.AugmentedFaceNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {

    // Used to store asynchronous tasks (like model loading) so they can be canceled if needed
    private final Set<CompletableFuture<?>> loaders = new HashSet<>();

    private ArFrontFacingFragment arFragment; // Fragment that provides the front-facing camera for AR
    private ArSceneView arSceneView;         // View that renders the AR scene

    private Texture faceTexture;             // Texture (image) applied to the face
    private ModelRenderable faceModel;       // 3D model applied to the face

    // Maps each detected face to its corresponding face node in the scene
    private final HashMap<AugmentedFace, AugmentedFaceNode> facesNodes = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the layout file to use for this activity
        setContentView(R.layout.activity_main);

        // Listen for fragments being attached so we can get a reference to ArFrontFacingFragment
        getSupportFragmentManager().addFragmentOnAttachListener(this::onAttachFragment);

        // Only add the fragment if it's the first creation (not on screen rotation, etc.)
        if (savedInstanceState == null) {
            // Check if AR is supported on this device
            if (Sceneform.isSupported(this)) {
                // Add the AR fragment to the activity
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, ArFrontFacingFragment.class, null)
                        .commit();
            }
        }

        // Start loading models and textures asynchronously
        loadModels();
        loadTextures();
    }

    // Called when a fragment is attached to the activity
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        // Check if it's the AR fragment we are interested in
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFrontFacingFragment) fragment;

            // Set a callback to run when the AR view is created
            arFragment.setOnViewCreatedListener(this::onViewCreated);
        }
    }

    // Called when the AR Scene View is ready
    public void onViewCreated(ArSceneView arSceneView) {
        this.arSceneView = arSceneView;

        // Make sure the camera feed renders before anything else (important for face occlusion)
        arSceneView.setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST);

        // Set a listener for face tracking updates
        arFragment.setOnAugmentedFaceUpdateListener(this::onAugmentedFaceTrackingUpdate);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cancel any unfinished loading tasks to prevent memory leaks
        for (CompletableFuture<?> loader : loaders) {
            if (!loader.isDone()) {
                loader.cancel(true);
            }
        }
    }

    // Loads the 3D model that will be applied to the user's face
    private void loadModels() {
        loaders.add(ModelRenderable.builder()
                .setSource(this, R.raw.fox) // 3D model file in res/raw (e.g. fox.glb)
                .setIsFilamentGltf(true)    // Use the glTF format
                .build()
                .thenAccept(model -> faceModel = model) // Save model when ready
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG).show();
                    return null;
                }));
    }

    // Loads the texture (image) that will be applied to the face mesh
    private void loadTextures() {
        loaders.add(Texture.builder()
                .setSource(this, R.raw.freckles)           // Image file in res/raw (e.g. freckles.png)
                .setUsage(Texture.Usage.COLOR_MAP)         // How the texture is used
                .build()
                .thenAccept(texture -> faceTexture = texture) // Save texture when ready
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load texture", Toast.LENGTH_LONG).show();
                    return null;
                }));
    }

    // Called whenever a face is detected or updated by ARCore
    public void onAugmentedFaceTrackingUpdate(AugmentedFace augmentedFace) {
        // Don't do anything until both the model and texture are ready
        if (faceModel == null || faceTexture == null) {
            return;
        }

        // Check if this face is already being tracked
        AugmentedFaceNode existingFaceNode = facesNodes.get(augmentedFace);

        switch (augmentedFace.getTrackingState()) {
            case TRACKING:
                // If it's a new face, add a new face node to the scene
                if (existingFaceNode == null) {
                    AugmentedFaceNode faceNode = new AugmentedFaceNode(augmentedFace);

                    // Attach the 3D model to the face
                    RenderableInstance modelInstance = faceNode.setFaceRegionsRenderable(faceModel);
                    modelInstance.setShadowCaster(false);  // No shadows cast
                    modelInstance.setShadowReceiver(true); // Receives shadows from other models

                    // Apply the texture to the face mesh
                    faceNode.setFaceMeshTexture(faceTexture);

                    // Add the face node to the scene
                    arSceneView.getScene().addChild(faceNode);

                    // Keep track of this face
                    facesNodes.put(augmentedFace, faceNode);
                }
                break;

            case STOPPED:
                // If the face is no longer tracked, remove it from the scene
                if (existingFaceNode != null) {
                    arSceneView.getScene().removeChild(existingFaceNode);
                }
                facesNodes.remove(augmentedFace);
                break;
        }
    }
}
