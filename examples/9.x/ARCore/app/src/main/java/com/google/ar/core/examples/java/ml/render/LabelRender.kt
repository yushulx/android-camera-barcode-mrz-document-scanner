/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.ml.render

import com.google.ar.core.Pose
import com.google.ar.core.examples.java.common.samplerender.Mesh
import com.google.ar.core.examples.java.common.samplerender.SampleRender
import com.google.ar.core.examples.java.common.samplerender.Shader
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Draws a label. See [draw].
 */
class LabelRender {
  companion object {
    private const val TAG = "LabelRender"
    val COORDS_BUFFER_SIZE = 2 * 4 * 4

    /**
     * Vertex buffer data for the mesh quad.
     */
    val NDC_QUAD_COORDS_BUFFER =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(
        ByteOrder.nativeOrder()
      ).asFloatBuffer().apply {
        put(
          floatArrayOf(
            /*0:*/
            -1.5f, -1.5f,
            /*1:*/
            1.5f, -1.5f,
            /*2:*/
            -1.5f, 1.5f,
            /*3:*/
            1.5f, 1.5f,
          )
        )
      }

    /**
     * Vertex buffer data for texture coordinates.
     */
    val SQUARE_TEX_COORDS_BUFFER =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(
        ByteOrder.nativeOrder()
      ).asFloatBuffer().apply {
        put(
          floatArrayOf(
            /*0:*/
            0f, 0f,
            /*1:*/
            1f, 0f,
            /*2:*/
            0f, 1f,
            /*3:*/
            1f, 1f,
          )
        )
      }
  }

  val cache = TextTextureCache()

  lateinit var mesh: Mesh
  lateinit var shader: Shader

  fun onSurfaceCreated(render: SampleRender) {
    shader = Shader.createFromAssets(render, "shaders/label.vert", "shaders/label.frag", null)
      .setBlend(
        Shader.BlendFactor.ONE, // ALPHA (src)
        Shader.BlendFactor.ONE_MINUS_SRC_ALPHA // ALPHA (dest)
      )
      .setDepthTest(false)
      .setDepthWrite(false)

    val vertexBuffers = arrayOf(
      VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER),
      VertexBuffer(render, 2, SQUARE_TEX_COORDS_BUFFER),
    )
    mesh = Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, vertexBuffers)
  }

  val labelOrigin = FloatArray(3)

  /**
   * Draws a label quad with text [label] at [pose]. The label will rotate to face [cameraPose] around the Y-axis.
   */
  fun draw(
    render: SampleRender,
    viewProjectionMatrix: FloatArray,
    pose: Pose,
    cameraPose: Pose,
    label: String
  ) {
    labelOrigin[0] = pose.tx()
    labelOrigin[1] = pose.ty()
    labelOrigin[2] = pose.tz()
    shader
      .setMat4("u_ViewProjection", viewProjectionMatrix)
      .setVec3("u_LabelOrigin", labelOrigin)
      .setVec3("u_CameraPos", cameraPose.translation)
      .setTexture("uTexture", cache.get(render, label))
    render.draw(mesh, shader)
  }
}