package com.airhockey.android.objects;

import com.airhockey.android.programs.ColorShaderProgram;
import com.example.infer.myapplication.Geometry;
import com.example.infer.myapplication.VertexArray;

import java.util.List;

public class Puck {
    private static final int POSITION_COMPONENT_COUNT = 3;
    public final float radius, height;

    private final VertexArray vertexArray;
    private final List<ObjectBuilder.DrawCommand> drawList;

    public Puck(float radius, float height, int numPointsAroundPuck)
    {
        ObjectBuilder.GeneratedData generatedData = ObjectBuilder.createPuck
                (new Geometry.Cylinder
                (new Geometry.Point(0f, 0f, 0f), radius, height), numPointsAroundPuck);

        this.radius = radius;
        this.height = height;

        vertexArray = new VertexArray(generatedData.vertexData);
        drawList = generatedData.drawList;

    }

    public void bindData(ColorShaderProgram colorProgram){
        vertexArray.setVertexAttribPointer(0, colorProgram.getPositionAttributeLocation(), POSITION_COMPONENT_COUNT, 0);

    }

    public void draw(){
        for(ObjectBuilder.DrawCommand drawCommand : drawList)
        {
            drawCommand.draw();
        }
    }


}
