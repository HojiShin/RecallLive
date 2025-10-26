package com.example.recalllive;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
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
 * FIXED: Quiz Fragment with proper question limiting and image orientation
 * - Always generates exactly TOTAL_QUESTIONS (10) questions
 * - Properly converts horizontal images to vertical
 * - Daily reset functionality
 */
public class PatientQuizFragment extends Fragment {
    private static final String TAG = "PatientQuizFragment";
    private static final int TOTAL_QUESTIONS = 10;

    private static final String PREFS_NAME = "RecallLiveQuizPrefs";
    private static final String KEY_LAST_QUIZ_DATE = "last_quiz_date";
    private static final String KEY_QUIZ_COMPLETED_TODAY = "quiz_completed_today";

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

    private String patientUid;
    private List<QuizQuestion> quizQuestions;
    private int currentQuestionIndex = 0;
    private int correctAnswers = 0;
    private boolean currentQuestionAnswered = false;
    private Random random;
    private SharedPreferences prefs;

    private FirebaseClusterManager clusterManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_patientquiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

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

        setupAnswerButtons();

        btnNextQuestion.setOnClickListener(v -> {
            currentQuestionIndex++;
            loadNextQuestion();
        });

        checkDailyReset();
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

    private void checkDailyReset() {
        String today = getTodayDateString();
        String lastQuizDate = prefs.getString(KEY_LAST_QUIZ_DATE, "");

        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "        QUIZ DAILY RESET CHECK");
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "Today: " + today);
        Log.d(TAG, "Last quiz date: " + lastQuizDate);

        if (!today.equals(lastQuizDate)) {
            Log.d(TAG, "🔄 New day detected - RESETTING QUIZ");

            prefs.edit()
                    .putString(KEY_LAST_QUIZ_DATE, today)
                    .putBoolean(KEY_QUIZ_COMPLETED_TODAY, false)
                    .apply();

            Log.d(TAG, "✓ Quiz reset for new day");
        } else {
            boolean completedToday = prefs.getBoolean(KEY_QUIZ_COMPLETED_TODAY, false);
            Log.d(TAG, "Same day - Quiz completed today: " + completedToday);
        }

        Log.d(TAG, "═══════════════════════════════════════════════════");
    }

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
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "        LOADING QUIZ DATA");
        Log.d(TAG, "═══════════════════════════════════════════════════");
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
                Log.e(TAG, "✗ Failed to load clusters: " + error);
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
     * FIXED: Generate exactly TOTAL_QUESTIONS (10) questions, no more
     */
    private void generateRandomQuestionsFromClusters(List<PhotoClusteringManager.PhotoCluster> clusters) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "     GENERATING RANDOM DAILY QUESTIONS");
        Log.d(TAG, "═══════════════════════════════════════════════════");

        List<PhotoClusteringManager.PhotoCluster> shuffledClusters = new ArrayList<>(clusters);
        Collections.shuffle(shuffledClusters, random);

        Log.d(TAG, "Shuffled " + shuffledClusters.size() + " clusters randomly");

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

        Collections.shuffle(allPhotos, random);
        Log.d(TAG, "Shuffled all photos randomly");

        // FIXED: Limit to exactly TOTAL_QUESTIONS
        int questionsToGenerate = Math.min(TOTAL_QUESTIONS, allPhotos.size());
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "GENERATING EXACTLY " + questionsToGenerate + " QUESTIONS");
        Log.d(TAG, "Total photos available: " + allPhotos.size());
        Log.d(TAG, "Questions to create: " + questionsToGenerate);
        Log.d(TAG, "═══════════════════════════════════════════════════");

        // CRITICAL: Clear any existing questions first
        quizQuestions.clear();

        // Generate exactly questionsToGenerate questions
        for (int i = 0; i < questionsToGenerate; i++) {
            PhotoData selectedPhoto = allPhotos.get(i);
            String correctAnswer = photoLocationMap.get(selectedPhoto);

            if (correctAnswer == null) {
                correctAnswer = "Unknown Location";
            }

            List<String> wrongAnswers = getRandomWorldLocations(3, correctAnswer);

            QuizQuestion question = new QuizQuestion();
            question.questionNumber = i + 1;
            question.photoUri = selectedPhoto.getPhotoUri();
            question.correctAnswer = correctAnswer;
            question.allAnswers = new ArrayList<>();
            question.allAnswers.add(question.correctAnswer);
            question.allAnswers.addAll(wrongAnswers);

            Collections.shuffle(question.allAnswers, random);

            quizQuestions.add(question);

            Log.d(TAG, "  Question " + question.questionNumber + "/" + questionsToGenerate + ": " + question.correctAnswer);
        }

        Log.d(TAG, "╔═══════════════════════════════════════════════╗");
        Log.d(TAG, "║  ✓ GENERATED " + quizQuestions.size() + " QUESTIONS ✓           ║");
        Log.d(TAG, "╚═══════════════════════════════════════════════╝");

        // VERIFICATION: Log final quiz size
        Log.d(TAG, "FINAL VERIFICATION:");
        Log.d(TAG, "  quizQuestions.size() = " + quizQuestions.size());
        Log.d(TAG, "  TOTAL_QUESTIONS = " + TOTAL_QUESTIONS);
        Log.d(TAG, "  Match: " + (quizQuestions.size() == questionsToGenerate));
        Log.d(TAG, "═══════════════════════════════════════════════════");

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

    private List<String> getRandomWorldLocations(int count, String correctAnswer) {
        List<String> availableLocations = new ArrayList<>(WORLD_LOCATIONS);

        availableLocations.removeIf(location ->
                location.equalsIgnoreCase(correctAnswer) ||
                        correctAnswer.toLowerCase().contains(location.split(",")[0].toLowerCase()) ||
                        location.toLowerCase().contains(correctAnswer.split(",")[0].toLowerCase())
        );

        Collections.shuffle(availableLocations, random);
        return availableLocations.subList(0, Math.min(count, availableLocations.size()));
    }

    private void loadNextQuestion() {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "Loading question index: " + currentQuestionIndex);
        Log.d(TAG, "Total questions in list: " + quizQuestions.size());
        Log.d(TAG, "═══════════════════════════════════════════════════");

        // FIXED: Proper bounds checking
        if (currentQuestionIndex >= quizQuestions.size()) {
            Log.d(TAG, "Reached end of quiz (index " + currentQuestionIndex + " >= size " + quizQuestions.size() + ")");
            showQuizComplete();
            return;
        }

        QuizQuestion question = quizQuestions.get(currentQuestionIndex);

        // Display progress correctly
        tvQuestionTitle.setText("Question " + (currentQuestionIndex + 1) + " of " + quizQuestions.size());
        tvQuestionText.setText("Where is this?");

        Log.d(TAG, "Displaying: Question " + (currentQuestionIndex + 1) + " of " + quizQuestions.size());

        // FIXED: Load image with proper orientation handling
        loadImageFromUri(question.photoUri);

        if (question.allAnswers.size() >= 4) {
            btnAnswerA.setText(question.allAnswers.get(0));
            btnAnswerB.setText(question.allAnswers.get(1));
            btnAnswerC.setText(question.allAnswers.get(2));
            btnAnswerD.setText(question.allAnswers.get(3));
        }

        resetButtonStates();

        tvFeedback.setVisibility(View.GONE);
        btnNextQuestion.setVisibility(View.GONE);

        currentQuestionAnswered = false;

        updateScore();
    }

    /**
     * FIXED: Load image with proper orientation and color for quiz
     */
    private void loadImageFromUri(String uriString) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        try {
            Uri uri = Uri.parse(uriString);
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);

            if (inputStream != null) {
                // FIXED: Decode with proper color configuration (same as video)
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.inPremultiplied = true;
                options.inDither = false;

                Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();

                if (bitmap != null) {
                    // FIXED: Convert horizontal images to vertical like in videos
                    Bitmap orientedBitmap = convertToPortraitForQuiz(bitmap);
                    ivQuestionImage.setImageBitmap(orientedBitmap);

                    if (orientedBitmap != bitmap) {
                        bitmap.recycle();
                    }
                } else {
                    ivQuestionImage.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                ivQuestionImage.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load image: " + uriString, e);
            ivQuestionImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    /**
     * FIXED: Convert horizontal images to vertical for quiz display
     * Same logic as video generation for consistency
     */
    private Bitmap convertToPortraitForQuiz(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // If already portrait or square, return as is
        if (height >= width) {
            return bitmap;
        }

        // Image is horizontal - rotate 90 degrees clockwise
        Log.d(TAG, "Converting landscape quiz image to portrait: " + width + "x" + height);

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);

        return rotated;
    }

    private void checkAnswer(Button clickedButton, String selectedAnswer) {
        QuizQuestion question = quizQuestions.get(currentQuestionIndex);

        if (selectedAnswer.equals(question.correctAnswer)) {
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
        // FIXED: Display score out of actual quiz size (not unlimited)
        tvScore.setText("Score: " + correctAnswers + "/" + quizQuestions.size());
    }

    private void showQuizComplete() {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "        QUIZ COMPLETED");
        Log.d(TAG, "═══════════════════════════════════════════════════");

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

        prefs.edit()
                .putBoolean(KEY_QUIZ_COMPLETED_TODAY, true)
                .apply();

        Log.d(TAG, "Score: " + correctAnswers + "/" + quizQuestions.size() + " (" + String.format("%.0f", percentage) + "%)");
        Log.d(TAG, "Quiz marked as completed for today");
        Log.d(TAG, "═══════════════════════════════════════════════════");

        btnNextQuestion.setVisibility(View.GONE);

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
                .addOnFailureListener(e -> Log.e(TAG, "✗ Failed to save quiz results", e));
    }

    private static class QuizQuestion {
        int questionNumber;
        String photoUri;
        String correctAnswer;
        List<String> allAnswers;
        boolean wasAnsweredCorrectly = false;
    }
}