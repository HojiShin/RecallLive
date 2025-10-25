package com.example.recalllive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class GuardianSettingsFragment extends Fragment {
    private TextView settingsToHistory;
    private TextView settingsToHelp;
    private TextView settingsToFeedback;
    private TextView settingsToTime;
    private TextView settingsToInfo;
    private TextView settingsToLogout;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_patientsettings, container, false);

        settingsToHistory = view.findViewById(R.id.tvHistory);
        settingsToHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                Intent intent = new Intent(getContext(), HistoryActivity.class);
                startActivity(intent);
            }
        });

        settingsToHelp = view.findViewById(R.id.tvHelp);
        settingsToHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                Intent intent = new Intent(getContext(), HelpActivity.class);
                startActivity(intent);
            }
        });

        settingsToTime = view.findViewById(R.id.tvRemindTime);
        settingsToTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                Intent intent = new Intent(getContext(), SetRemindTimeActivity.class);
                startActivity(intent);
            }
        });

        settingsToFeedback = view.findViewById(R.id.tvFeedback);
        settingsToFeedback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                Intent intent = new Intent(getContext(), SendFeedbackActivity.class);
                startActivity(intent);
            }
        });

        settingsToInfo = view.findViewById(R.id.tvAccountInfo);
        settingsToInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                Intent intent = new Intent(getContext(), GuardianAccountInfoActivity.class);
                startActivity(intent);
            }
        });

        settingsToLogout = view.findViewById(R.id.tvLogout);
        settingsToLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                Intent intent = new Intent(getContext(), LoginActivity.class);
                startActivity(intent);
            }
        });


        return view;
    }
}
