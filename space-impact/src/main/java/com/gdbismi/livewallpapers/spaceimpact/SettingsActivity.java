package com.gdbismi.livewallpapers.spaceimpact;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Launcher entry point and wallpaper settings: pick an LCD palette and apply the wallpaper. */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = getSharedPreferences(Palette.PREFS_NAME, MODE_PRIVATE);
        int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView intro = new TextView(this);
        intro.setText(R.string.settings_intro);
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        root.addView(intro);

        TextView heading = new TextView(this);
        heading.setText(R.string.settings_palette);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        heading.setPadding(0, pad, 0, dp(8));
        root.addView(heading);

        RadioGroup group = new RadioGroup(this);
        Palette current = Palette.load(prefs);
        for (Palette p : Palette.values()) {
            RadioButton button = new RadioButton(this);
            button.setId(p.ordinal() + 1);
            button.setText(p.labelRes);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            button.setPadding(dp(8), dp(12), dp(8), dp(12));
            group.addView(button);
        }
        group.check(current.ordinal() + 1);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                prefs.edit()
                        .putString(Palette.KEY_PALETTE, Palette.values()[checkedId - 1].name())
                        .apply();
            }
        });
        root.addView(group);

        Button apply = new Button(this);
        apply.setText(R.string.settings_apply);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWallpaperPicker();
            }
        });
        root.addView(apply, lp);

        TextView tip = new TextView(this);
        tip.setText(R.string.settings_tip);
        tip.setPadding(0, pad, 0, 0);
        root.addView(tip);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void openWallpaperPicker() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        new ComponentName(this, SpaceImpactWallpaperService.class));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, R.string.settings_no_picker, Toast.LENGTH_LONG).show();
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
