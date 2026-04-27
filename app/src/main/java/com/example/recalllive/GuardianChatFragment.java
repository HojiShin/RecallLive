package com.example.recalllive;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * ENHANCED Guardian Chat Fragment with proper Gemini integration
 */
public class GuardianChatFragment extends Fragment {

    private static final String TAG = "GuardianChatFragment";
    private static final String ARG_PATIENT_UID = "patient_uid";

    private String linkedPatientUid;
    private String guardianUid;

    // UI Components
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private Button sendButton;
    private ProgressBar loadingIndicator;
    private ChipGroup suggestedQuestionsChip;
    private TextView emptyStateText;
    private TextView emotionSummaryText;
    private LinearLayout chatContainer;

    // Services and Adapters
    private GeminiChatService geminiService;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private FirebaseFirestore firestore;

    public static GuardianChatFragment newInstance(String patientUid) {
        GuardianChatFragment fragment = new GuardianChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATIENT_UID, patientUid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            linkedPatientUid = getArguments().getString(ARG_PATIENT_UID);
        }

        // Get linked patient UID from SharedPreferences if not provided
        if (linkedPatientUid == null) {
            linkedPatientUid = requireContext().getSharedPreferences("RecallLivePrefs",
                    Context.MODE_PRIVATE).getString("linked_patient_uid", null);
        }

        // Get guardian UID
        guardianUid = requireContext().getSharedPreferences("RecallLivePrefs",
                Context.MODE_PRIVATE).getString("guardian_uid", null);

        messageList = new ArrayList<>();
        geminiService = new GeminiChatService(requireContext());
        firestore = FirebaseFirestore.getInstance();

        Log.d(TAG, "GuardianChatFragment created");
        Log.d(TAG, "Patient UID: " + linkedPatientUid);
        Log.d(TAG, "Guardian UID: " + guardianUid);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_guardian_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        chatRecyclerView = view.findViewById(R.id.chat_recycler_view);
        messageInput = view.findViewById(R.id.message_input);
        sendButton = view.findViewById(R.id.send_button);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        suggestedQuestionsChip = view.findViewById(R.id.suggested_questions);
        emptyStateText = view.findViewById(R.id.empty_state_text);
        emotionSummaryText = view.findViewById(R.id.emotion_summary_text);
        chatContainer = view.findViewById(R.id.chat_container);

        // Setup RecyclerView
        setupRecyclerView();

        // Setup send button
        sendButton.setOnClickListener(v -> sendMessage());

        // Setup enter key to send
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        // Setup suggested questions
        loadSuggestedQuestions();

        // Load emotion data and show welcome message
        loadEmotionDataAndShowWelcome();

        // Validate patient UID
        if (linkedPatientUid == null || linkedPatientUid.isEmpty()) {
            showError("No patient linked. Please link a patient first.");
            return;
        }
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true); // Start from bottom
        chatRecyclerView.setLayoutManager(layoutManager);

        chatAdapter = new ChatAdapter();
        chatRecyclerView.setAdapter(chatAdapter);
    }

    private void loadEmotionDataAndShowWelcome() {
        showLoading(true);

        geminiService.loadEmotionData(linkedPatientUid, new GeminiChatService.EmotionDataCallback() {
            @Override
            public void onEmotionDataLoaded(ChatMessage.EmotionContext emotionContext) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    showWelcomeMessage(emotionContext);
                    updateEmotionSummary(emotionContext);
                    loadSuggestedQuestions(); // Refresh suggestions based on data
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    showWelcomeMessage(null);
                    Log.w(TAG, "Could not load emotion data: " + error);
                });
            }
        });
    }

    private void showWelcomeMessage(ChatMessage.EmotionContext emotionContext) {
        String welcomeText;

        if (emotionContext != null && emotionContext.getTotalEmotions() > 0) {
            int positive = emotionContext.getHappy() + emotionContext.getSurprise();
            int negative = emotionContext.getSad() + emotionContext.getAngry() + emotionContext.getFear();
            int total = emotionContext.getTotalEmotions();

            double positivePercent = (positive * 100.0) / total;

            welcomeText = "👋 Welcome! I'm here to help you understand your loved one's emotional responses.\n\n" +
                    "📊 Summary:\n" +
                    "• " + emotionContext.getVideoCount() + " videos watched\n" +
                    "• " + total + " emotions detected\n" +
                    "• " + String.format("%.0f%%", positivePercent) + " positive reactions\n\n" +
                    "Ask me anything about these results or how to help!";
        } else {
            welcomeText = "👋 Welcome! I'm here to help you understand and respond to your loved one's emotions.\n\n" +
                    "Once they start watching videos, I'll analyze their facial expressions and help you understand what they mean.\n\n" +
                    "Feel free to ask me any questions!";
        }

        addSystemMessage(welcomeText);
    }

    private void updateEmotionSummary(ChatMessage.EmotionContext emotionContext) {
        if (emotionSummaryText == null || emotionContext == null) return;

        if (emotionContext.getTotalEmotions() > 0) {
            String summary = String.format("📊 %d videos • %d emotions detected",
                    emotionContext.getVideoCount(),
                    emotionContext.getTotalEmotions());
            emotionSummaryText.setText(summary);
            emotionSummaryText.setVisibility(View.VISIBLE);
        } else {
            emotionSummaryText.setVisibility(View.GONE);
        }
    }

    private void loadSuggestedQuestions() {
        if (suggestedQuestionsChip == null) return;

        suggestedQuestionsChip.removeAllViews();

        String[] questions = geminiService.getSuggestedQuestions();

        for (String question : questions) {
            Chip chip = new Chip(requireContext());
            chip.setText(question);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(android.R.color.white);
            chip.setTextColor(getResources().getColor(android.R.color.black));
            chip.setOnClickListener(v -> {
                messageInput.setText(question);
                sendMessage();
            });
            suggestedQuestionsChip.addView(chip);
        }
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();

        if (messageText.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide keyboard
        hideKeyboard();

        // Add user message to chat
        ChatMessage userMessage = new ChatMessage(messageText, ChatMessage.MessageType.USER);
        addMessage(userMessage);

        // Clear input
        messageInput.setText("");

        // Show loading
        showLoading(true);

        // Send to Gemini
        geminiService.sendMessage(messageText, linkedPatientUid, new GeminiChatService.ChatCallback() {
            @Override
            public void onResponse(String response) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    showLoading(false);

                    // Add AI response to chat
                    ChatMessage aiMessage = new ChatMessage(response, ChatMessage.MessageType.AI);
                    addMessage(aiMessage);

                    // Save conversation to Firestore (optional)
                    saveMessageToFirestore(userMessage);
                    saveMessageToFirestore(aiMessage);
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    showLoading(false);

                    String errorMessage = "Sorry, I encountered an error. Please try again.\n\nError: " + error;
                    ChatMessage errorMsg = new ChatMessage(errorMessage, ChatMessage.MessageType.AI);
                    addMessage(errorMsg);

                    Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void addMessage(ChatMessage message) {
        messageList.add(message);
        chatAdapter.submitList(new ArrayList<>(messageList));

        // Scroll to bottom
        if (chatRecyclerView != null && chatAdapter.getItemCount() > 0) {
            chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }

        // Hide empty state
        if (emptyStateText != null) {
            emptyStateText.setVisibility(View.GONE);
        }
    }

    private void addSystemMessage(String text) {
        ChatMessage systemMessage = new ChatMessage(text, ChatMessage.MessageType.SYSTEM);
        addMessage(systemMessage);
    }

    private void saveMessageToFirestore(ChatMessage message) {
        if (guardianUid == null || linkedPatientUid == null) {
            return;
        }

        message.setGuardianUid(guardianUid);
        message.setPatientUid(linkedPatientUid);
        message.setEmotionContext(geminiService.getCurrentEmotionContext());

        firestore.collection("guardian_chats")
                .add(message)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "✅ Message saved to Firestore: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save message to Firestore", e);
                });
    }

    private void showLoading(boolean show) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (sendButton != null) {
            sendButton.setEnabled(!show);
        }
    }

    private void showError(String error) {
        if (emptyStateText != null) {
            emptyStateText.setText(error);
            emptyStateText.setVisibility(View.VISIBLE);
        }
        if (chatContainer != null) {
            chatContainer.setVisibility(View.GONE);
        }
        Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
    }

    private void hideKeyboard() {
        if (getActivity() != null && getView() != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear context when leaving chat
        if (geminiService != null) {
            geminiService.clearContext();
        }
    }
}