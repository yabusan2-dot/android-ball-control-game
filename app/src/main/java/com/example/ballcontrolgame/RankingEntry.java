package com.example.ballcontrolgame;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class RankingEntry {
    public String name;
    public Object time; // StringかLongの両方に対応できるようObjectにする
    public long timestamp;

    public RankingEntry() {
        // Firebase 用のデフォルトコンストラクタ
    }

    public RankingEntry(String name, String time, long timestamp) {
        this.name = name;
        this.time = time;
        this.timestamp = timestamp;
    }

    // 表示用の文字列を安全に取得するメソッド
    public String getTimeString() {
        if (time == null) return "00:000";
        return String.valueOf(time);
    }
}
