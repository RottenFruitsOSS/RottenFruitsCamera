package com.rottenfruits.detector

/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.base.Objects
import com.rottenfruits.detector.camera.CameraSource
import com.rottenfruits.detector.camera.CameraSourcePreview
import com.rottenfruits.detector.camera.GraphicOverlay
import com.rottenfruits.detector.camera.WorkflowModel
import com.rottenfruits.detector.camera.WorkflowModel.WorkflowState
import com.rottenfruits.detector.objectdetection.ProminentObjectProcessor
import com.rottenfruits.detector.rottendetection.*
import com.rottenfruits.detector.settings.PreferenceUtils
import java.io.IOException

class CustomModelObjectDetectionActivity : AppCompatActivity(), OnClickListener {

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowState? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var searchButton: ExtendedFloatingActionButton? = null
    private var searchButtonAnimator: AnimatorSet? = null

    // customDialog
    private var labelsMap: Map<String, String>? = null
    private var descriptionMap: Map<String, String>? = null
    private var iconMap: Map<String, Int>? = null
    private var resultDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_live_custom_model_kotlin)
        preview = findViewById(R.id.camera_preview)
        graphicOverlay = findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            cameraSource = CameraSource(this)
        }
        promptChip = findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator =
            (AnimatorInflater.loadAnimator(this,
                R.animator.bottom_prompt_chip_enter
            ) as AnimatorSet).apply {
                setTarget(promptChip)
            }
        searchButton = findViewById<ExtendedFloatingActionButton>(R.id.product_search_button).apply {
            setOnClickListener(this@CustomModelObjectDetectionActivity)
        }
        searchButtonAnimator =
            (AnimatorInflater.loadAnimator(this,
                R.animator.search_button_enter
            ) as AnimatorSet).apply {
                setTarget(searchButton)
            }

        setUpWorkflowModel()
        setUpCustomDialog()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        resultDialog?.dismiss()
        workflowModel?.markCameraFrozen()

        if (!Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
        currentWorkflowState = WorkflowState.NOT_STARTED
        workflowModel?.setWorkflowState(WorkflowState.DETECTING)

        cameraSource?.setFrameProcessor(
            ProminentObjectProcessor(
                graphicOverlay!!, workflowModel!!
                ,
                CUSTOM_MODEL_PATH
            )
        )
        workflowModel?.setWorkflowState(WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    private fun startCameraPreview() {
        val cameraSource = this.cameraSource ?: return
        val workflowModel = this.workflowModel ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        if (workflowModel?.isCameraLive == true) {
            workflowModel!!.markCameraFrozen()
            preview?.stop()
        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java).apply {

            // Observes the workflow state changes, if happens, update the overlay view indicators and
            // camera preview state.
            workflowState.observe(this@CustomModelObjectDetectionActivity, Observer { workflowState ->
                if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                    return@Observer
                }
                currentWorkflowState = workflowState
                Log.d(TAG, "Current workflow state: ${workflowState.name}")

                if (PreferenceUtils.isAutoSearchEnabled(this@CustomModelObjectDetectionActivity)) {
                    stateChangeInAutoSearchMode(workflowState)
                } else {
                    stateChangeInManualSearchMode(workflowState)
                }
            })

            // Observes changes on the object to search, if happens, show detected object labels as
            // product search results.

            objectToSearch.observe(this@CustomModelObjectDetectionActivity, Observer { detectObject ->
                val rottenScoreList: List<RottenScore> = detectObject.labels.map { label ->
                    RottenScore("", label.text, "${label.confidence*100}" /* subtitle */)
                }
                workflowModel?.onSearchCompleted(detectObject, rottenScoreList)
            })

            // Observes changes on the object that has search completed, if happens, show the customDialog
            // to present search result.
            Log.e(TAG, "SDK Versopm: ${Build.VERSION.SDK_INT}")

            searchedObject.observe(this@CustomModelObjectDetectionActivity, Observer { searchedObject ->
                resultDialog = CustomDialog.build(this@CustomModelObjectDetectionActivity)
                    .title(
                        labelsMap!!.get(searchedObject.rottenScoreList[0].title)!!,
                        titleColor = ContextCompat.getColor(
                            this@CustomModelObjectDetectionActivity, R.color.primaryTextColor)
                    )
                    .body(
                        descriptionMap!!.get(searchedObject.rottenScoreList[0].title)!!,
                        //searchedObject.productList[0].subtitle,
                        color = ContextCompat.getColor(
                            this@CustomModelObjectDetectionActivity, R.color.product_description)
                    )
                    .icon(
                        iconMap!!.get(searchedObject.rottenScoreList[0].title)!!,
                        true
                    )
                    .background(
                        R.drawable.layout_rounded_cherry
                    )
                    .onPositive(
                        text = getString(R.string.done)
                    )
                    .onDismiss() {
                        workflowModel?.setWorkflowState(WorkflowState.DETECTING)
                    }
            })
        }
    }

    // mapping results with image classification model
    private fun setUpCustomDialog() {
        labelsMap = mapOf(
            Pair("normal", getString(R.string.dialog_result_normal)),
            Pair("spoiled_early", getString(R.string.dialog_result_spoiled_early)),
            Pair("spoiled_advanced", getString(R.string.dialog_result_spoiled_advanced))
        )

        descriptionMap = mapOf(
            Pair("normal", getString(R.string.dialog_result_normal_description)),
            Pair("spoiled_early", getString(R.string.dialog_result_spoiled_early_description)),
            Pair("spoiled_advanced", getString(R.string.dialog_result_spoiled_advanced_description))
        )

        iconMap = mapOf(
            Pair("normal", R.drawable.ic_sentiment_very_satisfied_black_24dp),
            Pair("spoiled_early", R.drawable.ic_sentiment_satisfied_black_24dp),
            Pair("spoiled_advanced", R.drawable.ic_sentiment_very_dissatisfied_black_24dp)
        )
    }

    private fun stateChangeInAutoSearchMode(workflowState: WorkflowState) {
        val wasPromptChipGone = promptChip!!.visibility == View.GONE

        searchButton?.visibility = View.GONE
        when (workflowState) {
            WorkflowState.DETECTING, WorkflowState.DETECTED, WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(
                    if (workflowState == WorkflowState.CONFIRMING)
                        R.string.prompt_hold_camera_steady
                    else
                        R.string.prompt_point_at_a_bird
                )
                startCameraPreview()
            }
            WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowState.SEARCHING -> {
                promptChip?.visibility = View.GONE
                stopCameraPreview()
            }
            WorkflowState.SEARCHED -> {
                stopCameraPreview()
            }
            else -> promptChip?.visibility = View.GONE
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
        if (shouldPlayPromptChipEnteringAnimation && promptChipAnimator?.isRunning == false) {
            promptChipAnimator?.start()
        }
    }

    private fun stateChangeInManualSearchMode(workflowState: WorkflowState) {
        val wasPromptChipGone = promptChip?.visibility == View.GONE
        val wasSearchButtonGone = searchButton?.visibility == View.GONE

        when (workflowState) {
            WorkflowState.DETECTING, WorkflowState.DETECTED, WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_point_at_an_object)
                searchButton?.visibility = View.GONE
                startCameraPreview()
            }
            WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.VISIBLE
                searchButton?.isEnabled = true
                searchButton?.setBackgroundColor(Color.WHITE)
                startCameraPreview()
            }
            WorkflowState.SEARCHING -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.VISIBLE
                searchButton?.isEnabled = false
                searchButton?.setBackgroundColor(Color.GRAY)
                stopCameraPreview()
            }
            WorkflowState.SEARCHED -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.GONE
                stopCameraPreview()
            }
            else -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.GONE
            }
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
        promptChipAnimator?.let {
            if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
        }

        val shouldPlaySearchButtonEnteringAnimation = wasSearchButtonGone && searchButton?.visibility == View.VISIBLE
        searchButtonAnimator?.let {
            if (shouldPlaySearchButtonEnteringAnimation && !it.isRunning) it.start()
        }
    }

    companion object {
        private const val TAG = "CustomModelODActivity"
        private const val CUSTOM_MODEL_PATH = "custom_models/cherry_model.tflite"
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.product_search_button -> {
                searchButton?.isEnabled = false
                workflowModel?.onSearchButtonClicked()
            }
        }
    }
}