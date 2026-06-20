package com.raincat.dolby_beta.view.proxy.configuration;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogInputItem;

/**
 * 代理源配置视图
 * 允许用户配置音源切换的代理源（空格隔开）
 */
public class ProxyOriginalView extends BaseDialogInputItem {
    public ProxyOriginalView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ProxyOriginalView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProxyOriginalView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.proxy_original_title;
        editView.setKeyListener(DigitsKeyListener.getInstance("qwertyuiopasdfghjklzxcvbnm "));
        setData(SettingHelper.getInstance().getProxyOriginal() + "", SettingHelper.proxy_original_default);

        defaultView.setOnClickListener(view -> {
            editView.setText(SettingHelper.proxy_original_default);
            editView.setSelection(editView.getText().length());
        });

        editView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                SettingHelper.getInstance().setProxyOriginal(editView.getText().toString());
            }
        });
    }
}
