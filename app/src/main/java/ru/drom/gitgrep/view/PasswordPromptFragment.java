package ru.drom.gitgrep.view;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.view.LayoutInflater;
import android.view.View;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.drom.gitgrep.R;

public final class PasswordPromptFragment extends DialogFragment implements DialogInterface.OnClickListener {
    public PasswordPromptFragment() {}

    @BindView(R.id.dlg_creds_login_input)
    TextInputEditText loginText;

    @BindView(R.id.dlg_creds_login_input_layout)
    TextInputLayout loginLayout;

    @BindView(R.id.dlg_creds_pass_input)
    TextInputEditText passText;

    @BindView(R.id.dlg_creds_pass_input_layout)
    TextInputLayout passLayout;

    @Override
    @SuppressLint("InflateParams")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.dlg_creds_input, null, false);

        ButterKnife.bind(this, view);

        return new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setView(view)
                .setPositiveButton(android.R.string.ok, this)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        final String name = loginText.getText().toString();
        final String pass = passText.getText().toString();

        ((CredentialsReceiver) getActivity()).onCredentials(name, pass);
    }

    public interface CredentialsReceiver {
        void onCredentials(String login, String pass);
    }
}