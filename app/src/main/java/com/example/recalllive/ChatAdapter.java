package com.example.recalllive;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * FIXED ChatAdapter - Properly styled chat messages with emotion context
 */
public class ChatAdapter extends ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;
    private static final int VIEW_TYPE_SYSTEM = 3;

    public ChatAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ChatMessage>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
                    if (oldItem.getMessageId() != null && newItem.getMessageId() != null) {
                        return oldItem.getMessageId().equals(newItem.getMessageId());
                    }
                    // Fallback: compare by content and timestamp
                    return oldItem.getContent().equals(newItem.getContent()) &&
                            oldItem.getTimestamp() != null &&
                            newItem.getTimestamp() != null &&
                            oldItem.getTimestamp().equals(newItem.getTimestamp());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
                    return oldItem.getContent().equals(newItem.getContent()) &&
                            oldItem.getType().equals(newItem.getType());
                }
            };

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = getItem(position);
        ChatMessage.MessageType type = message.getMessageType();

        switch (type) {
            case USER:
                return VIEW_TYPE_USER;
            case AI:
                return VIEW_TYPE_AI;
            case SYSTEM:
                return VIEW_TYPE_SYSTEM;
            default:
                return VIEW_TYPE_SYSTEM;
        }
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = getItem(position);
        int viewType = getItemViewType(position);
        holder.bind(message, viewType);
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final CardView messageCard;
        private final TextView messageText;
        private final TextView timestampText;
        private final LinearLayout messageContainer;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageCard = itemView.findViewById(R.id.message_card);
            messageText = itemView.findViewById(R.id.message_text);
            timestampText = itemView.findViewById(R.id.timestamp_text);
            messageContainer = itemView.findViewById(R.id.message_container);
        }

        public void bind(ChatMessage message, int viewType) {
            messageText.setText(message.getContent());

            // Format timestamp
            if (message.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
                String time = sdf.format(message.getTimestamp().toDate());
                timestampText.setText(time);
                timestampText.setVisibility(View.VISIBLE);
            } else {
                timestampText.setVisibility(View.GONE);
            }

            // Style based on message type
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();

            switch (viewType) {
                case VIEW_TYPE_USER:
                    // User messages: right-aligned, blue
                    params.gravity = Gravity.END;
                    messageCard.setCardBackgroundColor(Color.rgb(33, 150, 243)); // Blue
                    messageText.setTextColor(Color.WHITE);
                    timestampText.setTextColor(Color.rgb(200, 230, 255));
                    messageContainer.setPadding(60, 8, 8, 8);
                    break;

                case VIEW_TYPE_AI:
                    // AI messages: left-aligned, dark gray
                    params.gravity = Gravity.START;
                    messageCard.setCardBackgroundColor(Color.rgb(66, 66, 66)); // Dark gray
                    messageText.setTextColor(Color.WHITE);
                    timestampText.setTextColor(Color.rgb(180, 180, 180));
                    messageContainer.setPadding(8, 8, 60, 8);
                    break;

                case VIEW_TYPE_SYSTEM:
                    // System messages: centered, light gray, smaller
                    params.gravity = Gravity.CENTER;
                    messageCard.setCardBackgroundColor(Color.rgb(158, 158, 158)); // Gray
                    messageText.setTextColor(Color.WHITE);
                    messageText.setTextSize(12);
                    timestampText.setVisibility(View.GONE);
                    messageContainer.setPadding(40, 4, 40, 4);
                    break;
            }

            messageCard.setLayoutParams(params);
        }
    }
}