package fmr.haxbdex.paintballhaxbdex;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.AugmentedFaceNode;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = AppCompatActivity.class.getSimpleName();

    private static final double MIN_OPENGL_VERSION = 3.0;
    //    private ModelRenderable faceRegionsRenderable;
//    private Texture faceMeshTexture;
    private CustomArFragment arFragment;

    private final HashMap<AugmentedFace, AugmentedFaceNode> faceNodeMap = new HashMap<>();

    private Scene scene;
    private Camera camera;
    private ModelRenderable bulletRenderable;
    private boolean shouldStartTimer = true;
    private int balloonsLeft = 20;
    private Point point;
    private TextView yourHealth;
    private TextView oppponentHealth;
    private SoundPool soundPool;
    private int sound;
    private int youInitialHealth = 100;
    private int opponentInitialHealth = 100;

    private ModelRenderable andyRenderable;

    private ModelRenderable anotherAndyRenderable;

    private AnchorNode anchorNode;

    DatabaseReference playerOneRefDb = null;
    DatabaseReference playerTwoRefDb = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }


        Display display = getWindowManager().getDefaultDisplay();
        point = new Point();
        display.getRealSize(point);

        setContentView(R.layout.activity_main);

        loadSoundPool();

        yourHealth = findViewById(R.id.yourHealth);
        oppponentHealth = findViewById(R.id.opponentHealth);
        yourHealth.setText("" + youInitialHealth);
        oppponentHealth.setText("" + opponentInitialHealth);

        setupFireBase();
        arFragment =
                (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);

//        addArFaceToScreen();

        scene = arFragment.getArSceneView().getScene();
        camera = scene.getCamera();

        addBalloonsToScene();
        buildBulletModel();


        ImageButton shoot = findViewById(R.id.shootButton);

        shoot.setOnClickListener(v -> {

            if (shouldStartTimer) {
                startTimer();
                shouldStartTimer = false;
            }

            shoot();

        });

        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(renderable -> andyRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onSceneUpdate);


        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(renderable -> anotherAndyRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (anotherAndyRenderable == null) {
                        return;
                    }

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    // Create the transformable andy and add it to the anchor.
                    TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
                    andy.setParent(anchorNode);
                    andy.setRenderable(anotherAndyRenderable);
                    andy.select();
                });


    }

    private void setupFireBase() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference rootRef = database.getReference();
        playerOneRefDb = rootRef.child("players").child("player_one");
        playerTwoRefDb = rootRef.child("players").child("player_two");
        playerOneRefDb.setValue("" + youInitialHealth);
        playerTwoRefDb.setValue("" + opponentInitialHealth);

        playerOneRefDb.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                String value = dataSnapshot.getValue(String.class);
                Log.d(TAG, "Value is: " + value);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

        playerTwoRefDb.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                String value = dataSnapshot.getValue(String.class);
                Log.d(TAG, "Value is: " + value);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

    }

    private void onSceneUpdate(FrameTime frameTime) {
        // Let the fragment update its state first.
        arFragment.onUpdate(frameTime);

        ArSceneView arSceneView = arFragment.getArSceneView();

        // If there is no frame then don't process anything.
        if (arFragment.getArSceneView().getArFrame() == null) {
            return;
        }

        // If ARCore is not tracking yet, then don't process anything.
        if (arFragment.getArSceneView().getArFrame().getCamera().getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        // Place the anchor 1m in front of the camera if anchorNode is null.
        if (this.anchorNode == null) {
            Session session = arFragment.getArSceneView().getSession();

            Frame frame = arSceneView.getArFrame();
            float x = frame.getCamera().getPose().qx();
            float y = frame.getCamera().getPose().qy();
            float z = frame.getCamera().getPose().qz();
            Node andy = new Node();
            andy.setParent(arSceneView.getScene().getCamera());
//            andy.setLocalPosition(new Vector3(position.x, position.y, position.z));
//            andy.setLocalPosition(new Vector3(0f,0,-1f));
//            andy.setLocalPosition(new Vector3(x, y, z));
            andy.setLocalPosition(new Vector3(0.5f, 0.5f, 0f));
            andy.setRenderable(andyRenderable);

        }
    }

    private void loadSoundPool() {

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_GAME)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();

        sound = soundPool.load(this, R.raw.blop_sound, 1);

    }

    private void shoot() {

        Ray ray = camera.screenPointToRay(point.x / 2f, point.y / 2f);
        Node node = new Node();
        node.setRenderable(bulletRenderable);
        scene.addChild(node);

        new Thread(() -> {
            boolean flag = false;

            for (int i = 0; i < 200; i++) {

                int finalI = i;
                runOnUiThread(() -> {

                    Vector3 vector3 = ray.getPoint(finalI * 0.1f);
                    node.setWorldPosition(vector3);

                    Node nodeInContact = scene.overlapTest(node);

                    if (nodeInContact != null && !flag) {
                        if (null != nodeInContact.getParent() && (nodeInContact.getParent() instanceof Camera || nodeInContact.getParent() instanceof AnchorNode)) {
                            Toast.makeText(this, "yoyo", Toast.LENGTH_LONG);
                            Log.d("APPPP", "im the target");
                            opponentInitialHealth = opponentInitialHealth - 10;
                            playerTwoRefDb.setValue("" + opponentInitialHealth);
                            oppponentHealth.setText("Opponent : " + opponentInitialHealth);
                        } else {
                            balloonsLeft--;
//                            balloonsLeftTxt.setText("Balloons Left: " + balloonsLeft);
                            scene.removeChild(nodeInContact);
                            soundPool.play(sound, 1f, 1f, 1, 0
                                    , 1f);
                        }
                        scene.removeChild(node);
                    }

                });

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

            runOnUiThread(() -> scene.removeChild(node));

        }).start();

    }

    private void startTimer() {

        TextView timer = findViewById(R.id.timerText);

        new Thread(() -> {

            int seconds = 0;

            while (balloonsLeft > 0) {

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                seconds++;

                int minutesPassed = seconds / 60;
                int secondsPassed = seconds % 60;

                runOnUiThread(() -> timer.setText(minutesPassed + ":" + secondsPassed));

            }

        }).start();

    }

    private void buildBulletModel() {

        Texture
                .builder()
                .setSource(this, R.drawable.texture)
                .build()
                .thenAccept(texture -> {


                    MaterialFactory
                            .makeOpaqueWithTexture(this, texture)
                            .thenAccept(material -> {

                                bulletRenderable = ShapeFactory
                                        .makeSphere(0.09f,
                                                new Vector3(0f, 0f, 0f),
                                                material);

                            });


                });

    }

    private void addBalloonsToScene() {

        ModelRenderable
                .builder()
                .setSource(this, Uri.parse("balloon.sfb"))
                .build()
                .thenAccept(renderable -> {

                    for (int i = 0; i < 20; i++) {

                        Node node = new Node();
                        node.setRenderable(renderable);
                        scene.addChild(node);


                        Random random = new Random();
                        int x = random.nextInt(10);
                        int z = random.nextInt(10);
                        int y = random.nextInt(20);

                        z = -z;

//                        Vector3 temp = new Vector3(0.5f,0.5f,-0f);
//                        node.setWorldPosition(new Vector3(temp));
                        node.setWorldPosition(new Vector3(
                                (float) x,
                                y / 10f,
                                (float) z
                        ));


                    }

                });

    }


    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (ArCoreApk.getInstance().checkAvailability(activity)
                == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
            Log.e(TAG, "Augmented Faces requires ARCore.");
            Toast.makeText(activity, "Augmented Faces requires ARCore", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }
}