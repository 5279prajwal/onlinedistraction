package com.example.focusblocker;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;

public class OverlayQuestionActivity extends Activity {

    private ArrayList<String> questions;
    private int currentIndex = 0;
    private TextView questionText;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make full-screen overlay
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        // Block touch outside and back button
        setFinishOnTouchOutside(false);

        setContentView(R.layout.activity_overlay_question);

        questionText = findViewById(R.id.questionText);
        nextButton = findViewById(R.id.nextButton);

        loadQuestions(); // Load 5 random questions
        displayCurrentQuestion();

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                currentIndex++;
                if (currentIndex < questions.size()) {
                    displayCurrentQuestion();
                } else {
                    finish(); // Exit the overlay
                }
            }
        });
    }

    private void loadQuestions() {
        // Master list of all possible questions
        ArrayList<String> allQuestions = new ArrayList<>();
        allQuestions.add("What is your main goal this week?");
        allQuestions.add("How does using this app help or hurt your goal?");
        allQuestions.add("What could you be doing instead?");
        allQuestions.add("Are you avoiding something important?");
        allQuestions.add("Will this help your future self?");
        allQuestions.add("Do you really need this now?");
        allQuestions.add("What happens if you skip this app today?");
        allQuestions.add("Is this a habit or a choice?");
        allQuestions.add("What outcome do you want by the end of today?");
        allQuestions.add("Can you work on a task for 10 minutes instead?");
        allQuestions.add("Is this the best use of your energy?");
        allQuestions.add("What’s something meaningful you can do right now?");
        allQuestions.add("If you stop now, what can you achieve instead?");
        allQuestions.add("What are your goals for this month?");
        allQuestions.add("Are you living intentionally right now?");

        // Shuffle the list and take 5 random questions
        Collections.shuffle(allQuestions);
        questions = new ArrayList<>(allQuestions.subList(0, 5));
    }

    private void displayCurrentQuestion() {
        questionText.setText(questions.get(currentIndex));
    }

    @Override
    public void onBackPressed() {
        // Disable back button
    }
}
