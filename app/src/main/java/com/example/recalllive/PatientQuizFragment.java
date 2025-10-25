package com.example.recalllive;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * FIXED: Quiz Fragment with DAILY RESET
 * - Generates new random questions every day
 * - Uses random clusters and photos
 * - Resets at midnight like videos
 */
public class PatientQuizFragment extends Fragment {
    private static final String TAG = "PatientQuizFragment";
    private static final int TOTAL_QUESTIONS = 10;

    // SharedPreferences for daily reset
    private static final String PREFS_NAME = "RecallLiveQuizPrefs";
    private static final String KEY_LAST_QUIZ_DATE = "last_quiz_date";
    private static final String KEY_QUIZ_COMPLETED_TODAY = "quiz_completed_today";

    // Random world locations for wrong answers
    private static final List<String> WORLD_LOCATIONS = Arrays.asList(
            "Paris, France", "Tokyo, Japan", "New York, USA", "London, England",
            "Sydney, Australia", "Rome, Italy", "Berlin, Germany", "Madrid, Spain",
            "Beijing, China", "Moscow, Russia", "Cairo, Egypt", "Mumbai, India",
            "Rio de Janeiro, Brazil", "Toronto, Canada", "Dubai, UAE",
            "Bangkok, Thailand", "Istanbul, Turkey", "Seoul, South Korea",
            "Mexico City, Mexico", "Singapore", "Amsterdam, Netherlands",
            "Barcelona, Spain", "Athens, Greece", "Vienna, Austria",
            "Buenos Aires, Argentina", "Los Angeles, USA", "Chicago, USA",
            "San Francisco, USA", "Boston, USA", "Miami, USA", "Seattle, USA",
            "Las Vegas, USA", "Vancouver, Canada", "Montreal, Canada",
            "Lima, Peru", "Santiago, Chile", "Bogota, Colombia",
            "Copenhagen, Denmark", "Stockholm, Sweden", "Oslo, Norway",
            "Helsinki, Finland", "Warsaw, Poland", "Prague, Czech Republic",
            "Budapest, Hungary", "Lisbon, Portugal", "Dublin, Ireland",
            "Brussels, Belgium", "Zurich, Switzerland", "Geneva, Switzerland",
            "Milan, Italy", "Venice, Italy", "Florence, Italy", "Naples, Italy",
            "Manchester, England", "Liverpool, England", "Edinburgh, Scotland",
            "Glasgow, Scotland", "Cardiff, Wales", "Belfast, Northern Ireland"
    );

    // UI Elements
    private TextView tvQuestionTitle;
    private TextView tvQuestionText;
    private ImageView ivQuestionImage;
    private Button btnAnswerA;
    private Button btnAnswerB;
    private Button btnAnswerC;
    private Button btnAnswerD;
    private TextView tvFeedback;
    private Button btnNextQuestion;
    private TextView tvScore;

    // Data
    private String patientUid;
    private List<QuizQuestion> quizQuestions;
    private int currentQuestionIndex = 0;
    private int correctAnswers = 0;
    private boolean currentQuestionAnswered = false;
    private Random random;
    private SharedPreferences prefs;

    // Firebase
    private FirebaseClusterManager clusterManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_patientquiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize preferences
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Initialize views
        tvQuestionTitle = view.findViewById(R.id.tv_question_title);
        tvQuestionText = view.findViewById(R.id.tv_question_text);
        ivQuestionImage = view.findViewById(R.id.iv_question_image);
        btnAnswerA = view.findViewById(R.id.btn_answer_a);
        btnAnswerB = view.findViewById(R.id.btn_answer_b);
        btnAnswerC = view.findViewById(R.id.btn_answer_c);
        btnAnswerD = view.findViewById(R.id.btn_answer_d);
        tvFeedback = view.findViewById(R.id.tv_feedback);
        btnNextQuestion = view.findViewById(R.id.btn_next_question);
        tvScore = view.findViewById(R.id.tv_score);

        // Initialize data
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            patientUid = auth.getCurrentUser().getUid();
        } else {
            Log.e(TAG, "No user logged in");
            Toast.makeText(getContext(), "Please log in to take quiz", Toast.LENGTH_LONG).show();
            return;
        }

        random = new Random();
        quizQuestions = new ArrayList<>();

        // Setup answer button listeners
        setupAnswerButtons();

        // Setup next button
        btnNextQuestion.setOnClickListener(v -> {
            currentQuestionIndex++;
            loadNextQuestion();
        });

        // Check if quiz needs reset
        checkDailyReset();

        // Load quiz data
        loadQuizData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clusterManager = null;
        quizQuestions = null;
        if (ivQuestionImage != null) {
            ivQuestionImage.setImageDrawable(null);
        }
        Log.d(TAG, "Quiz fragment destroyed");
    }

    /**
     * Check if quiz needs daily reset
     */
    private void checkDailyReset() {
        String today = getTodayDateString();
        String lastQuizDate = prefs.getString(KEY_LAST_QUIZ_DATE, "");

        Log.d(TAG, "════════════════════════════════════════════════════");
        Log.d(TAG, "        QUIZ DAILY RESET CHECK");
        Log.d(TAG, "════════════════════════════════════════════════════");
        Log.d(TAG, "Today: " + today);
        Log.d(TAG, "Last quiz date: " + lastQuizDate);

        if (!today.equals(lastQuizDate)) {
            Log.d(TAG, "🔄 New day detected - RESETTING QUIZ");

            // Reset quiz state
            prefs.edit()
                    .putString(KEY_LAST_QUIZ_DATE, today)
                    .putBoolean(KEY_QUIZ_COMPLETED_TODAY, false)
                    .apply();

            Log.d(TAG, "✓ Quiz reset for new day");
        } else {
            boolean completedToday = prefs.getBoolean(KEY_QUIZ_COMPLETED_TODAY, false);
            Log.d(TAG, "Same day - Quiz completed today: " + completedToday);
        }

        Log.d(TAG, "════════════════════════════════════════════════════");
    }

    /**
     * Get today's date string
     */
    private String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void setupAnswerButtons() {
        View.OnClickListener answerClickListener = v -> {
            if (currentQuestionAnswered) {
                return;
            }
            Button clickedButton = (Button) v;
            String selectedAnswer = clickedButton.getText().toString();
            checkAnswer(clickedButton, selectedAnswer);
        };

        btnAnswerA.setOnClickListener(answerClickListener);
        btnAnswerB.setOnClickListener(answerClickListener);
        btnAnswerC.setOnClickListener(answerClickListener);
        btnAnswerD.setOnClickListener(answerClickListener);
    }

    private void loadQuizData() {
        Log.d(TAG, "════════════════════════════════════════════════════");
        Log.d(TAG, "        LOADING QUIZ DATA");
        Log.d(TAG, "════════════════════════════════════════════════════");
        Log.d(TAG, "Patient: " + patientUid);

        tvQuestionTitle.setText("Loading Quiz...");
        tvQuestionText.setText("Generating today's random quiz questions...");

        clusterManager = new FirebaseClusterManager(requireContext(), patientUid);
        clusterManager.getClusters(new FirebaseClusterManager.OnClustersRetrievedCallback() {
            @Override
            public void onClustersRetrieved(List<PhotoClusteringManager.PhotoCluster> clusters) {
                if (clusters == null || clusters.isEmpty()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(),
                                    "No photos available for quiz. Please take photos!",
                                    Toast.LENGTH_LONG).show();
                            tvQuestionTitle.setText("No Data");
                            tvQuestionText.setText("Take photos with GPS to play quiz");
                        });
                    }
                    return;
                }

                Log.d(TAG, "✓ Found " + clusters.size() + " clusters");

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvQuestionText.setText("Loading location names...");
                    });
                }

                generateQuizQuestions(clusters);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to load clusters: " + error);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(),
                                "Failed to load quiz: " + error,
                                Toast.LENGTH_LONG).show();
                        tvQuestionTitle.setText("Error");
                        tvQuestionText.setText("Could not load quiz data");
                    });
                }
            }
        });
    }

    private void generateQuizQuestions(List<PhotoClusteringManager.PhotoCluster> clusters) {
        Log.d(TAG, "Generating random quiz questions for today...");

        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Fragment not attached");
            return;
        }

        LocationGeocoderService geocoder = new LocationGeocoderService(getContext());
        geocoder.geocodeClusters(clusters, new LocationGeocoderService.ClusterGeocodeCallback() {
            @Override
            public void onProgress(int processed, int total) {
                Log.d(TAG, "  Geocoding: " + processed + "/" + total);
            }

            @Override
            public void onComplete(List<PhotoClusteringManager.PhotoCluster> geocodedClusters) {
                if (!isAdded() || getContext() == null) {
                    Log.w(TAG, "Fragment detached during geocoding");
                    return;
                }
                generateRandomQuestionsFromClusters(geocodedClusters);
            }
        });
    }

    /**
     * Generate RANDOM questions from available clusters
     * NEW: Shuffles clusters and photos for daily variety
     */
    private void generateRandomQuestionsFromClusters(List<PhotoClusteringManager.PhotoCluster> clusters) {
        Log.d(TAG, "════════════════════════════════════════════════════");
        Log.d(TAG, "     GENERATING RANDOM DAILY QUESTIONS");
        Log.d(TAG, "════════════════════════════════════════════════════");

        // Shuffle clusters for randomness
        List<PhotoClusteringManager.PhotoCluster> shuffledClusters = new ArrayList<>(clusters);
        Collections.shuffle(shuffledClusters, random);

        Log.d(TAG, "Shuffled " + shuffledClusters.size() + " clusters randomly");

        // Collect ALL photos from ALL clusters
        List<PhotoData> allPhotos = new ArrayList<>();
        Map<PhotoData, String> photoLocationMap = new HashMap<>();

        for (PhotoClusteringManager.PhotoCluster cluster : shuffledClusters) {
            if (cluster.getPhotos() != null && cluster.getPhotoCount() > 0) {
                String location = cluster.getLocationName();
                if (location == null || location.isEmpty()) {
                    location = "Unknown Location";
                }

                for (PhotoData photo : cluster.getPhotos()) {
                    allPhotos.add(photo);
                    photoLocationMap.put(photo, location);
                }
            }
        }

        Log.d(TAG, "Collected " + allPhotos.size() + " photos from all clusters");

        if (allPhotos.isEmpty()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(),
                            "No photos found. Take photos with GPS!",
                            Toast.LENGTH_LONG).show();
                    tvQuestionTitle.setText("No Photos");
                    tvQuestionText.setText("Take photos to generate quiz!");
                });
            }
            return;
        }

        // SHUFFLE ALL PHOTOS for maximum randomness
        Collections.shuffle(allPhotos, random);
        Log.d(TAG, "Shuffled all photos randomly");

        // Generate questions
        int questionsToGenerate = Math.min(TOTAL_QUESTIONS, allPhotos.size());
        Log.d(TAG, "Generating " + questionsToGenerate + " random questions");

        for (int i = 0; i < questionsToGenerate; i++) {
            PhotoData selectedPhoto = allPhotos.get(i);
            String correctAnswer = photoLocationMap.get(selectedPhoto);

            if (correctAnswer == null) {
                correctAnswer = "Unknown Location";
            }

            // Get 3 random wrong answers
            List<String> wrongAnswers = getRandomWorldLocations(3, correctAnswer);

            // Create question
            QuizQuestion question = new QuizQuestion();
            question.questionNumber = i + 1;
            question.photoUri = selectedPhoto.getPhotoUri();
            question.correctAnswer = correctAnswer;
            question.allAnswers = new ArrayList<>();
            question.allAnswers.add(question.correctAnswer);
            question.allAnswers.addAll(wrongAnswers);

            // Shuffle answer order
            Collections.shuffle(question.allAnswers, random);

            quizQuestions.add(question);

            Log.d(TAG, "  Question " + question.questionNumber + ": " + question.correctAnswer);
        }

        Log.d(TAG, "╔═══════════════════════════════════════╗");
        Log.d(TAG, "║  ✓ GENERATED " + quizQuestions.size() + " RANDOM QUESTIONS ✓  ║");
        Log.d(TAG, "╚═══════════════════════════════════════╝");
        Log.d(TAG, "════════════════════════════════════════════════════");

        // Load first question
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (quizQuestions.size() > 0) {
                    currentQuestionIndex = 0;
                    loadNextQuestion();
                } else {
                    Toast.makeText(getContext(),
                            "Could not generate quiz questions",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * Get random world locations (excluding similar to correct answer)
     */
    private List<String> getRandomWorldLocations(int count, String correctAnswer) {
        List<String> availableLocations = new ArrayList<>(WORLD_LOCATIONS);

        // Remove locations too similar to correct answer
        availableLocations.removeIf(location ->
                location.equalsIgnoreCase(correctAnswer) ||
                        correctAnswer.toLowerCase().contains(location.split(",")[0].toLowerCase()) ||
                        location.toLowerCase().contains(correctAnswer.split(",")[0].toLowerCase())
        );

        // Shuffle and pick
        Collections.shuffle(availableLocations, random);
        return availableLocations.subList(0, Math.min(count, availableLocations.size()));
    }

    private void loadNextQuestion() {
        Log.d(TAG, "Loading question " + (currentQuestionIndex + 1) + "/" + quizQuestions.size());

        if (currentQuestionIndex >= quizQuestions.size()) {
            showQuizComplete();
            return;
        }

        QuizQuestion question = quizQuestions.get(currentQuestionIndex);

        // Update UI
        tvQuestionTitle.setText("Question " + question.questionNumber + " of " + quizQuestions.size());
        tvQuestionText.setText("Where is this?");

        // Load image
        loadImageFromUri(question.photoUri);

        // Set answers
        if (question.allAnswers.size() >= 4) {
            btnAnswerA.setText(question.allAnswers.get(0));
            btnAnswerB.setText(question.allAnswers.get(1));
            btnAnswerC.setText(question.allAnswers.get(2));
            btnAnswerD.setText(question.allAnswers.get(3));
        }

        // Reset button states
        resetButtonStates();

        // Hide feedback and next button
        tvFeedback.setVisibility(View.GONE);
        btnNextQuestion.setVisibility(View.GONE);

        // Reset answered flag
        currentQuestionAnswered = false;

        // Update score
        updateScore();
    }

    private void loadImageFromUri(String uriString) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        try {
            Uri uri = Uri.parse(uriString);
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);

            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                ivQuestionImage.setImageBitmap(bitmap);
                inputStream.close();
            } else {
                ivQuestionImage.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load image: " + uriString, e);
            ivQuestionImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    private void checkAnswer(Button clickedButton, String selectedAnswer) {
        QuizQuestion question = quizQuestions.get(currentQuestionIndex);

        if (selectedAnswer.equals(question.correctAnswer)) {
            // CORRECT
            clickedButton.setBackgroundColor(Color.rgb(76, 175, 80));
            clickedButton.setTextColor(Color.WHITE);

            tvFeedback.setText("✓ Correct! This is " + question.correctAnswer);
            tvFeedback.setTextColor(Color.rgb(76, 175, 80));
            tvFeedback.setVisibility(View.VISIBLE);

            disableAllAnswerButtons();
            btnNextQuestion.setVisibility(View.VISIBLE);
            currentQuestionAnswered = true;

            if (!question.wasAnsweredCorrectly) {
                correctAnswers++;
                question.wasAnsweredCorrectly = true;
                updateScore();
            }

        } else {
            // WRONG
            clickedButton.setBackgroundColor(Color.rgb(244, 67, 54));
            clickedButton.setTextColor(Color.WHITE);

            tvFeedback.setText("✗ Try again!");
            tvFeedback.setTextColor(Color.rgb(244, 67, 54));
            tvFeedback.setVisibility(View.VISIBLE);

            clickedButton.setEnabled(false);
        }
    }

    private void resetButtonStates() {
        Button[] buttons = {btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD};
        for (Button button : buttons) {
            button.setBackgroundColor(Color.rgb(224, 224, 224));
            button.setTextColor(Color.BLACK);
            button.setEnabled(true);
        }
    }

    private void disableAllAnswerButtons() {
        btnAnswerA.setEnabled(false);
        btnAnswerB.setEnabled(false);
        btnAnswerC.setEnabled(false);
        btnAnswerD.setEnabled(false);
    }

    private void updateScore() {
        tvScore.setText("Score: " + correctAnswers + "/" + quizQuestions.size());
    }

    private void showQuizComplete() {
        Log.d(TAG, "════════════════════════════════════════════════════");
        Log.d(TAG, "        QUIZ COMPLETED");
        Log.d(TAG, "════════════════════════════════════════════════════");

        tvQuestionTitle.setText("Quiz Complete!");
        tvQuestionText.setText("");
        ivQuestionImage.setImageResource(android.R.drawable.ic_menu_info_details);

        btnAnswerA.setVisibility(View.GONE);
        btnAnswerB.setVisibility(View.GONE);
        btnAnswerC.setVisibility(View.GONE);
        btnAnswerD.setVisibility(View.GONE);

        double percentage = (correctAnswers * 100.0) / quizQuestions.size();
        String feedback = String.format("You got %d out of %d correct!\n\nScore: %.0f%%",
                correctAnswers, quizQuestions.size(), percentage);

        if (percentage >= 80) {
            feedback += "\n\n🎉 Excellent work!";
        } else if (percentage >= 60) {
            feedback += "\n\n👍 Good job!";
        } else {
            feedback += "\n\n💪 Keep practicing!";
        }

        feedback += "\n\n🔄 Come back tomorrow for new questions!";

        tvFeedback.setText(feedback);
        tvFeedback.setTextColor(Color.rgb(33, 150, 243));
        tvFeedback.setVisibility(View.VISIBLE);

        // Mark quiz as completed today
        prefs.edit()
                .putBoolean(KEY_QUIZ_COMPLETED_TODAY, true)
                .apply();

        Log.d(TAG, "Score: " + correctAnswers + "/" + quizQuestions.size() + " (" + String.format("%.0f", percentage) + "%)");
        Log.d(TAG, "Quiz marked as completed for today");
        Log.d(TAG, "════════════════════════════════════════════════════");

        // Hide next button (quiz is done for today)
        btnNextQuestion.setVisibility(View.GONE);

        // Save results
        saveQuizResults(percentage);
    }

    private void saveQuizResults(double percentage) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        DatabaseReference quizRef = FirebaseDatabase.getInstance().getReference()
                .child("Patient")
                .child(patientUid)
                .child("quizResults")
                .push();

        Map<String, Object> results = new HashMap<>();
        results.put("timestamp", System.currentTimeMillis());
        results.put("date", getTodayDateString());
        results.put("totalQuestions", quizQuestions.size());
        results.put("correctAnswers", correctAnswers);
        results.put("percentage", percentage);

        quizRef.setValue(results)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✓ Quiz results saved to Firebase"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to save quiz results", e));
    }

    private static class QuizQuestion {
        int questionNumber;
        String photoUri;
        String correctAnswer;
        List<String> allAnswers;
        boolean wasAnsweredCorrectly = false;
    }
}