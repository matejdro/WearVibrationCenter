package com.matejdro.wearvibrationcenter.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NotificationTextParser {
	public String title;
	public String text;

	public NotificationTextParser()
	{
		this.title = null;
		this.text = "";
    }

    public void parse(ProcessedNotification target, Notification source)
    {
        if (!tryParseNatively(source))
        {
            parseFromNotificationView(source);
        }

        target.setText(text);
        target.setTitle(title);
    }
	
	@SuppressWarnings("ConstantConditions") //Android Studio does not correlate between containsKey() and get() and thus throws warnings
    @TargetApi(value = Build.VERSION_CODES.JELLY_BEAN)
	public boolean tryParseNatively(Notification notification)
	{
		Bundle extras = NotificationCompat.getExtras(notification);
		if (extras == null)
			return false;

        if (parseMessageStyleNotification(notification, extras))
            return true;

        if (extras.containsKey(Notification.EXTRA_TEXT_LINES) && extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).length > 0)
        {
            if (parseInboxNotification(extras))
                return true;
        }

		if (!extras.containsKey(Notification.EXTRA_TEXT) && !extras.containsKey(Notification.EXTRA_TEXT_LINES) && !extras.containsKey(Notification.EXTRA_BIG_TEXT))
		{
			return false;
		}
		
		if (extras.containsKey(Notification.EXTRA_TITLE_BIG))
		{
			CharSequence bigTitle = extras.getCharSequence(NotificationCompat.EXTRA_TITLE_BIG);
            if (bigTitle != null && (bigTitle.length() < 40 || !extras.containsKey(Notification.EXTRA_TITLE)))
				title = bigTitle.toString();
			else
				title = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_TITLE));
		}
		else if (extras.containsKey(NotificationCompat.EXTRA_TITLE))
            title = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_TITLE));

        if (extras.containsKey(Notification.EXTRA_TEXT_LINES))
        {
            for (CharSequence line : extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES))
            {
                text += formatCharSequence(line) + "\n\n";
            }
            text = text.trim();
        }
        else if (extras.containsKey(Notification.EXTRA_BIG_TEXT))
        {
            text = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT));
        }
        else
        {
            text = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_TEXT));
        }

        if (extras.containsKey(Notification.EXTRA_SUB_TEXT))
        {
            text = text.trim();
            text= text + "\n\n" + formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT));
        }


        return true;
	}

    public boolean parseMessageStyleNotification(Notification notification, Bundle extras)
    {
        NotificationCompat.MessagingStyle messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (messagingStyle == null)
            return false;

        title = formatCharSequence(messagingStyle.getConversationTitle());
        if (TextUtils.isEmpty(title))
            title = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_TITLE_BIG));
        if (TextUtils.isEmpty(title))
            title = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_TITLE));
        if (title == null)
            title  = "";

        List<NotificationCompat.MessagingStyle.Message> messagesDescending = new ArrayList<>(messagingStyle.getMessages());
        Collections.sort(messagesDescending, new Comparator<NotificationCompat.MessagingStyle.Message>() {
            @Override
            public int compare(NotificationCompat.MessagingStyle.Message m1, NotificationCompat.MessagingStyle.Message m2) {
                return (int) (m2.getTimestamp() - m1.getTimestamp());
            }
        });

        text = "";
        for (NotificationCompat.MessagingStyle.Message message : messagesDescending)
        {
            String sender;
            if (message.getSender() == null)
                sender = formatCharSequence(messagingStyle.getUserDisplayName());
            else
                sender = formatCharSequence(message.getSender());

            text += sender + ": " + message.getText() + "\n";
        }

        return true;
    }

    @SuppressWarnings("ConstantConditions")
    @TargetApi(value = Build.VERSION_CODES.JELLY_BEAN)
    public boolean parseInboxNotification(Bundle extras)
    {
        if (extras.containsKey(Notification.EXTRA_SUMMARY_TEXT))
            title = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_SUMMARY_TEXT));
        else if (extras.containsKey(Notification.EXTRA_SUB_TEXT))
            title = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT));
        else if (extras.containsKey(Notification.EXTRA_TITLE))
            title = formatCharSequence(extras.getCharSequence(NotificationCompat.EXTRA_TITLE));
        else
            return false;

        CharSequence[] lines = extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES);

        int i = 0;
        while (true)
        {
            text += formatCharSequence(lines[i]) + "\n\n";

                i++;
                if (i >= lines.length)
                    break;
        }

        text = text.trim();

        return true;
    }

    private String formatCharSequence(CharSequence sequence)
    {
        if (sequence == null)
            return "";

        if (!(sequence instanceof SpannableString))
        {
            return sequence.toString();
        }

        SpannableString spannableString = (SpannableString) sequence;
        String text = spannableString.toString();

        StyleSpan[] spans = spannableString.getSpans(0, spannableString.length(), StyleSpan.class);

        int amountOfBoldspans = 0;

       for (int i = spans.length - 1; i >= 0; i--)
       {
          StyleSpan span = spans[i];
          if (span.getStyle() == Typeface.BOLD)
          {
              amountOfBoldspans++;
          }
       }

        if (amountOfBoldspans == 1)
        {
            for (int i = spans.length - 1; i >= 0; i--)
            {
                StyleSpan span = spans[i];
                if (span.getStyle() == Typeface.BOLD)
                {
                    text = insertString(text, "\n",  spannableString.getSpanEnd(span));
                    break;
                }
            }
        }

        return text;
    }

    private static String insertString(String text, String insert, int pos)
    {
        return text.substring(0, pos).trim().concat(insert).trim().concat(text.substring(pos)).trim();
    }

	private void getExtraData(Notification notification) {
		@SuppressWarnings("deprecation") //contentView is only deprecated for writing, not reading
        RemoteViews views = notification.contentView;
		if (views == null) {
			return;
		}

		parseRemoteView(views);
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void parseFromNotificationView(Notification notification) {
		RemoteViews views;
		try {
            //noinspection deprecation
            views = notification.bigContentView;
		} catch (NoSuchFieldError e) {
			getExtraData(notification);
			return;
		}
		if (views == null) {
			getExtraData(notification);
			return;
		}

		parseRemoteView(views);
	}

	private void parseRemoteView(RemoteViews views)
	{
		try {
			Class remoteViewsClass = RemoteViews.class;
			Class baseActionClass = Class.forName("android.widget.RemoteViews$Action");


			Field actionsField = remoteViewsClass.getDeclaredField("mActions");

			actionsField.setAccessible(true);

			@SuppressWarnings("unchecked")
            ArrayList<Object> actions = (ArrayList<Object>) actionsField.get(views);
			for (Object action : actions) {
                if (!action.getClass().getName().contains("$ReflectionAction"))
					continue;

				Field typeField = action.getClass().getDeclaredField("type");
				typeField.setAccessible(true);
				int type = typeField.getInt(action);
                if (type != 9 && type != 10)
					continue;


				int viewId = -1;
				try
				{
					Field idField = baseActionClass.getDeclaredField("viewId");
					idField.setAccessible(true);
					viewId = idField.getInt(action);
				}
				catch (NoSuchFieldException ignored)
				{
				}

				Field valueField = action.getClass().getDeclaredField("value");
				valueField.setAccessible(true);
				CharSequence value = (CharSequence) valueField.get(action);
				
				if (value == null ||
                    value.equals("...") ||
                    isInteger(value.toString()) ||
                    text.contains(value))
                {
					continue;
				}

				if (viewId == android.R.id.title)
				{
					if (title == null || title.length() < value.length())
						title = value.toString().trim();
				}
				else
					text += formatCharSequence(value) + "\n\n";

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean isInteger(String input) {
		try {
			Integer.parseInt(input);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}