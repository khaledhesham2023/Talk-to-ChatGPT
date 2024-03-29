package com.example.speechtotextandanswerapp.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.speechtotextandanswerapp.R
import com.example.speechtotextandanswerapp.base.BaseFragment
import com.example.speechtotextandanswerapp.databinding.FragmentMainBinding
import com.example.speechtotextandanswerapp.ui.model.Message
import com.example.speechtotextandanswerapp.ui.model.Question
import com.example.speechtotextandanswerapp.ui.model.request.ChatRequest
import com.example.speechtotextandanswerapp.ui.model.request.TextToSpeechRequest
import com.example.speechtotextandanswerapp.utils.ViewState
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.util.Date
import java.util.Random


@AndroidEntryPoint
class MainFragment : BaseFragment<FragmentMainBinding, MainViewModel>() {
    override val layout: Int
        get() = R.layout.fragment_main
    override val viewModelClass: Class<MainViewModel>
        get() = MainViewModel::class.java

    private lateinit var chatList: ArrayList<Question>
    private lateinit var questionsAdapter: QuestionsAdapter
    private lateinit var gptMessages: ArrayList<Message>
    private var isPlaying = false
    private val requestCode = 200
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var askedQuestion: String
    private lateinit var savedQuestionAudioFile: File
    private lateinit var savedAnswerAudioFile: File
    private lateinit var request: String
    private lateinit var response: String
    private lateinit var answerText: String
    private lateinit var responseAudio: MediaPlayer
    private lateinit var voiceFiles: MutableList<File>
    private lateinit var voiceMultipartFiles: MutableList<MultipartBody.Part>
    private var id: Long = 0

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatList = ArrayList()
        voiceMultipartFiles = ArrayList()
        voiceFiles = mutableListOf()
        gptMessages = ArrayList()
        questionsAdapter = QuestionsAdapter(chatList)
        getMicrophonePermission()
        viewBinding.answers.adapter = questionsAdapter
        viewBinding.answers.layoutManager = LinearLayoutManager(requireContext())
        viewModel.getQuestions()
        setupListeners()
        setupObservers()
    }


    @SuppressLint("SetTextI18n")
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupListeners() {
        viewBinding.record.setOnClickListener {
            isPlaying = !isPlaying
            if (isPlaying) {
                setupRecordingFile()
                viewBinding.clickToRecord.text = getString(R.string.speak_now)
                viewBinding.record.setImageResource(R.drawable.ic_record)
                configureMediaRecorder()
            } else {
                viewBinding.clickToRecord.text =
                    getString(R.string.click_on_record_button_to_record_voice)
                viewBinding.record.setImageResource(R.drawable.ic_mic)
                releaseMediaRecorder()
                val requestFile = RequestBody.create(MultipartBody.FORM, savedQuestionAudioFile)
                val body = MultipartBody.Part.createFormData(
                    "file",
                    savedQuestionAudioFile.name,
                    requestFile
                )
                viewModel.convertSpeechToText(
                    body,
                    MultipartBody.Part.createFormData("model", "whisper-1")
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    private fun setupObservers() {
        viewModel.convertSpeechToTextLiveData.observe(viewLifecycleOwner) {
            when (it) {
                is ViewState.Loading -> {
                    loadingDialog.show()
                }

                is ViewState.Success -> {
                    askedQuestion = it.data.text!!
                    gptMessages.add(Message(content = it.data.text))
                    request = Gson().toJson(ChatRequest(messages = gptMessages)).toString()
                    viewModel.getChatResponse(ChatRequest(messages = gptMessages))
                }

                is ViewState.Error -> {
                    Toast.makeText(
                        requireContext(),
                        "Error in converting speech to text",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingDialog.dismiss()
                }
            }
        }
        viewModel.getChatResponseLiveData.observe(viewLifecycleOwner) {
            when (it) {
                is ViewState.Loading -> {
                    loadingDialog.show()
                }

                is ViewState.Success -> {
                    response = Gson().toJson(it.data).toString()
                    answerText = it.data.choices?.get(0)?.message!!.content!!
                    viewModel.convertTextToSpeech(TextToSpeechRequest(input = answerText))
                }

                is ViewState.Error -> {
                    Toast.makeText(
                        requireContext(),
                        "Error in getting chat response",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingDialog.dismiss()
                }
            }
        }

        viewModel.saveQuestionLiveData.observe(viewLifecycleOwner) {
            when (it) {
                is ViewState.Loading -> {
                    loadingDialog.show()
                }

                is ViewState.Success -> {
                    id = it.data.id
                    viewModel.getQuestions()
                }

                is ViewState.Error -> {
                    Toast.makeText(requireContext(), "Error in saving question", Toast.LENGTH_SHORT)
                        .show()
                    loadingDialog.dismiss()
                }
            }
        }

        viewModel.getQuestionsLiveData.observe(viewLifecycleOwner) {
            when (it) {
                is ViewState.Loading -> {
                    loadingDialog.show()
                }

                is ViewState.Success -> {
                    questionsAdapter.updateDataSet(it.data)
                    loadingDialog.dismiss()
                }

                is ViewState.Error -> {
                    Toast.makeText(
                        requireContext(),
                        "Error in get questions from database",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingDialog.dismiss()
                }
            }
        }
        viewModel.convertTextToSpeechLiveData.observe(viewLifecycleOwner) {
            when (it) {
                is ViewState.Loading -> {
                    loadingDialog.show()
                }

                is ViewState.Success -> {
                    saveTheFileToTheDevice(it.data)
                    respondToUser()
                    val question = MultipartBody.Part.createFormData("question", askedQuestion)
                    val questionAudioFile = MultipartBody.Part.createFormData(
                        "question_file", savedQuestionAudioFile.name,
                        RequestBody.create(MultipartBody.FORM, savedQuestionAudioFile)
                    )
                    val answer = MultipartBody.Part.createFormData("answer",answerText)
                    val answerAudioFile = MultipartBody.Part.createFormData("answer_file",savedAnswerAudioFile.name,
                        RequestBody.create(MultipartBody.FORM,savedAnswerAudioFile))
                    val requestData = MultipartBody.Part.createFormData("request",request)
                    val responseData = MultipartBody.Part.createFormData("response",response)
                    viewModel.saveQuestion(answer,answerAudioFile,question,questionAudioFile,requestData,responseData)
                }

                is ViewState.Error -> {
                    Toast.makeText(
                        requireContext(),
                        "Error in converting response to speech",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingDialog.dismiss()
                }
            }
        }
        viewModel.saveQuestionLiveData.observe(viewLifecycleOwner) {
            when (it) {
                is ViewState.Loading -> loadingDialog.show()
                is ViewState.Success -> {
                    viewModel.getQuestions()
                }

                is ViewState.Error -> {
                    Toast.makeText(
                        requireContext(),
                        "Error during inserting the question",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingDialog.dismiss()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                requestCode
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupRecordingFile() {
        val contextWrapper = ContextWrapper(requireContext())
        val recDirectory = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
        savedQuestionAudioFile = File(recDirectory, generateFileName("question"))
        voiceFiles.add(savedQuestionAudioFile)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun configureMediaRecorder() {
        mediaRecorder = MediaRecorder()
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder!!.setOutputFile(savedQuestionAudioFile)
        mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder!!.prepare()
        mediaRecorder!!.start()
    }

    private fun releaseMediaRecorder() {
        mediaRecorder!!.stop()
        mediaRecorder!!.release()
        mediaRecorder = null
    }

    private fun generateFileName(type: String): String {
        var fileName = ""
        val baseFileName = "${Date().time}${Random().nextInt(1000000000)}"
        when (type) {
            "question" -> {
                fileName = "question-$baseFileName.mp3"
            }

            "answer" -> {
                fileName = "answer-$baseFileName.mp3"
            }
        }
        return fileName
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun saveTheFileToTheDevice(audioData: ByteArray) {
        val contextWrapper = ContextWrapper(requireContext())
        val recDirectory = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
        savedAnswerAudioFile = File(recDirectory, generateFileName("answer"))
        savedAnswerAudioFile.writeBytes(audioData)
        voiceFiles.add(savedAnswerAudioFile)
    }

    private fun respondToUser() {
        responseAudio = MediaPlayer.create(requireContext(), Uri.fromFile(savedAnswerAudioFile))
        responseAudio.start()
    }
}