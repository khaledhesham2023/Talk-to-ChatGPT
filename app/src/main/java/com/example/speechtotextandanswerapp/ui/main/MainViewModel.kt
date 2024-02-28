package com.example.speechtotextandanswerapp.ui.main

import android.content.Context
import android.net.http.HttpException
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresExtension
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.speechtotextandanswerapp.datasource.UseCases
import com.example.speechtotextandanswerapp.ui.model.Choice
import com.example.speechtotextandanswerapp.ui.model.Message
import com.example.speechtotextandanswerapp.ui.model.Question
import com.example.speechtotextandanswerapp.ui.model.request.ChatRequest
import com.example.speechtotextandanswerapp.ui.model.request.QuestionRequest
import com.example.speechtotextandanswerapp.ui.model.response.BaseResponse
import com.example.speechtotextandanswerapp.ui.model.response.ChatResponse
import com.example.speechtotextandanswerapp.ui.model.response.SpeechResponse
import com.example.speechtotextandanswerapp.utils.ApiRequestManager
import com.example.speechtotextandanswerapp.utils.ViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(@ApplicationContext private val context: Context,private val useCases: UseCases, private val apiRequestManager: ApiRequestManager) : ViewModel() {

    private var _getChatResponseLiveData = MutableLiveData<ViewState<ArrayList<Message>?>>()
    val getChatResponseLiveData:LiveData<ViewState<ArrayList<Message>?>>
        get() = _getChatResponseLiveData

    private var _getQuestionsLiveData = MutableLiveData<ViewState<ArrayList<Question>>>()
    val getQuestionsLiveData : MutableLiveData<ViewState<ArrayList<Question>>>
        get() = _getQuestionsLiveData

    private var _saveQuestionLiveData = MutableLiveData<ViewState<BaseResponse>>()
    val saveQuestionLiveData:LiveData<ViewState<BaseResponse>>
        get() = _saveQuestionLiveData

    private var _getSpeechResponseLiveData = MutableLiveData<ViewState<SpeechResponse>>()
    val getSpeechResponseLiveData:LiveData<ViewState<SpeechResponse>>
        get() = _getSpeechResponseLiveData


    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    fun getChatResponse(request: ChatRequest) {
        viewModelScope.launch {
            apiRequestManager.requestApi({useCases.getChatResponse(request)},_getChatResponseLiveData)
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    fun getQuestions() = viewModelScope.launch {
        apiRequestManager.requestApi({useCases.getQuestions()},_getQuestionsLiveData)

    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    fun saveQuestion(request: QuestionRequest) = viewModelScope.launch {
        apiRequestManager.requestApi({useCases.saveQuestion(request)},_saveQuestionLiveData)
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    fun getSpeechResponse(file: MultipartBody.Part, model:MultipartBody.Part) = viewModelScope.launch {
            apiRequestManager.requestApi({useCases.getSpeechResponse(file, model)},_getSpeechResponseLiveData)

    }
}