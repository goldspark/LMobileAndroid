package com.example.infer.myapplication;

import android.content.Context;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.airhockey.android.objects.Mallet;
import com.airhockey.android.objects.Puck;
import com.airhockey.android.objects.Table;
import com.airhockey.android.programs.ColorShaderProgram;
import com.airhockey.android.programs.TextureShaderProgram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES30.*;
import static android.opengl.Matrix.invertM;
import static android.opengl.Matrix.multiplyMM;
import static android.opengl.Matrix.multiplyMV;
import static android.opengl.Matrix.orthoM;
import static android.opengl.Matrix.rotateM;
import static android.opengl.Matrix.setIdentityM;
import static android.opengl.Matrix.setLookAtM;
import static android.opengl.Matrix.translateM;
import static com.example.infer.myapplication.Geometry.*;


public class AirHockeyRenderer implements GLSurfaceView.Renderer {
    private final Context context;

    private final float leftBond = -0.5f;
    private final float rightBound = 0.5f;
    private final float farBound = -0.8f;
    private final float nearBound = 0.8f;
    private Point puckPosition;
    private Vector puckVector;

    private final float projectionMatrix[] = new float[16];
    private final float modelMatrix[]= new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] viewProjectionMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    private final float[] invertedViewProjectionMatrix = new float[16];

    private boolean malletPressed = false;
    private Geometry.Point blueMalletPosition;
    private Geometry.Point previousBlueMalletPosition;

    private Table table;
    private Mallet mallet;
    private Puck puck;

    private TextureShaderProgram textureProgram;
    private ColorShaderProgram colorProgram;

    private int texture;

    public AirHockeyRenderer(Context context){
        this.context = context;

    }

    private void divideByW(float vector[])
    {
        vector[0] /= vector[3];
        vector[1] /= vector[3];
        vector[2] /= vector[3];
    }

    private Geometry.Ray convertNormalized2DPointToRay(float normalizedX, float normalizedY){
        // We'll convert these normalized device coordinates into world-space
        // coordinates. We'll pick a point on the near and far planes, and draw a
        // line between them. To do this transform, we need to first multiply by
        // the inverse matrix, and then we need to undo the perspective divide.
        final float[] nearPointNdc = {normalizedX, normalizedY, -1, 1};
        final float[] farPointNdc =  {normalizedX, normalizedY,  1, 1};

        final float[] nearPointWorld = new float[4];
        final float[] farPointWorld = new float[4];

        multiplyMV(
                nearPointWorld, 0, invertedViewProjectionMatrix, 0, nearPointNdc, 0);
        multiplyMV(
                farPointWorld, 0, invertedViewProjectionMatrix, 0, farPointNdc, 0);

        // Why are we dividing by W? We multiplied our vector by an inverse
        // matrix, so the W value that we end up is actually the *inverse* of
        // what the projection matrix would create. By dividing all 3 components
        // by W, we effectively undo the hardware perspective divide.
        divideByW(nearPointWorld);
        divideByW(farPointWorld);

        // We don't care about the W value anymore, because our points are now
        // in world coordinates.
        Point nearPointRay =
                new Point(nearPointWorld[0], nearPointWorld[1], nearPointWorld[2]);

        Point farPointRay =
                new Point(farPointWorld[0], farPointWorld[1], farPointWorld[2]);

        return new Ray(nearPointRay,
                Geometry.vectorBetween(nearPointRay, farPointRay));
    }

    public void handleTouchPress(float normalizedX, float normalizedY){
        Geometry.Ray ray = convertNormalized2DPointToRay(normalizedX, normalizedY);

        Geometry.Sphere malletBoundingSphere = new Geometry.Sphere(new Geometry.Point(blueMalletPosition.x, blueMalletPosition.y, blueMalletPosition.z), mallet.height/2f);

        malletPressed = Geometry.intersects(malletBoundingSphere, ray);

    }

    public void handleTouchDrag(float normalizedX, float normalizedY){
        if(malletPressed){
            Log.v("Touch", "pressed");
            Geometry.Ray ray = convertNormalized2DPointToRay(normalizedX, normalizedY);
            Geometry.Plane plane = new Geometry.Plane(new Geometry.Point(0, 0, 0), new Geometry.Vector(0,1,0));

            Geometry.Point touchedPoint = Geometry.intersectionPoint(ray, plane);
            ;
            ;
            previousBlueMalletPosition = blueMalletPosition;
            blueMalletPosition = new Geometry.Point(clamp(touchedPoint.x, leftBond + mallet.radius, rightBound - mallet.radius),mallet.height/2f,
                    clamp(touchedPoint.z, 0f + mallet.radius, nearBound - mallet.radius));

            float distance = Geometry.vectorBetween(blueMalletPosition, puckPosition).length();

            if(distance < (puck.radius + mallet.radius))
            {
                puckVector = Geometry.vectorBetween(previousBlueMalletPosition, blueMalletPosition);
            }

        }
    }
    private float clamp(float value, float min, float max)
    {
        return Math.min(max, Math.max(value, min));
    }

    private void positionTableInScene(){
        setIdentityM(modelMatrix, 0);
        rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f);
        multiplyMM(modelViewProjectionMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0);

    }

    private void positionObjectInScene(float x, float y, float z) {
        setIdentityM(modelMatrix, 0);
        translateM(modelMatrix, 0, x, y, z);
        multiplyMM(modelViewProjectionMatrix, 0, viewProjectionMatrix,
                0, modelMatrix, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        table = new Table();
        mallet = new Mallet(0.08f, 0.15f, 32);
        puck = new Puck(0.06f, 0.02f, 32);
        puckPosition = new Point(0f, puck.height/2f, 0f);
        puckVector = new Vector(0f, 0f, 0f);
        blueMalletPosition = new Geometry.Point(0f, mallet.height / 2f, 0.4f);

        textureProgram = new TextureShaderProgram(context);
        colorProgram = new ColorShaderProgram(context);

        texture = TextureHelper.loadTexture(context, R.drawable.air_hockey_surface);

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

       glViewport(0, 0, width, height);

       final float aspectRatio = width > height ?
               (float)width / (float) height :
               (float)height / (float)width;
      /*
       if(width > height){
           orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f);
       }
       else{
           orthoM(projectionMatrix, 0, -1f, 1f, -aspectRatio, aspectRatio, -1f, 1f);
       }
       */
        MatrixHelper.perspectiveM(projectionMatrix, 45, (float)width/(float)height, 1f, 10f);
        setLookAtM(viewMatrix, 0, 0f, 1.2f, 2.2f,0f,0f,0f,0f,1f,0f);


      //  setIdentityM(modelMatrix, 0);
      //  translateM(modelMatrix, 0, 0f, 0f, -3.0f);
       // rotateM(modelMatrix, 0, -60f, 1f, 0f, 0f);
       // final float temp[] = new float[16];
       // multiplyMM(temp, 0, projectionMatrix, 0, modelMatrix, 0);
       // System.arraycopy(temp, 0, projectionMatrix, 0, temp.length);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        puckPosition = puckPosition.translate(puckVector);
        if(puckPosition.x < leftBond + puck.radius ||
           puckPosition.x > rightBound - puck.radius){
            puckVector = new Vector(-puckVector.x, puckVector.y, puckVector.z);
            puckVector = puckVector.scale(0.9f);
        }
        if(puckPosition.z < farBound + puck.radius
           || puckPosition.z > nearBound - puck.radius)
        {
            puckVector = new Vector(puckVector.x, puckVector.y, -puckVector.z);
            puckVector = puckVector.scale(0.9f);
        }


        puckPosition = new Point(
                clamp(puckPosition.x, leftBond + puck.radius, rightBound - puck.radius),
                puckPosition.y,
                clamp(puckPosition.z, farBound + puck.radius, nearBound - puck.radius)
        );


        puckVector = puckVector.scale(0.99f);

       glClear(GL_COLOR_BUFFER_BIT);
       multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
       invertM(invertedViewProjectionMatrix, 0, viewProjectionMatrix, 0);

       positionTableInScene();
       textureProgram.useProgram();
       textureProgram.setUniforms(modelViewProjectionMatrix, texture);
       table.bindData(textureProgram);
       table.draw();



        positionObjectInScene(0f, mallet.height / 2f, -0.4f);
        colorProgram.useProgram();
        colorProgram.setUniforms(modelViewProjectionMatrix, 1f, 0f, 0f);
        mallet.bindData(colorProgram);
        mallet.draw();

        positionObjectInScene(blueMalletPosition.x, blueMalletPosition.y, blueMalletPosition.z);
        colorProgram.setUniforms(modelViewProjectionMatrix, 0f, 0f, 1f);
        mallet.draw();

        // Draw the puck.
        positionObjectInScene(puckPosition.x, puckPosition.y, puckPosition.z);
        colorProgram.setUniforms(modelViewProjectionMatrix, 0.8f, 0.8f, 1f);
        puck.bindData(colorProgram);
        puck.draw();







    }



}
