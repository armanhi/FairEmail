package eu.faircode.email;

/*
    This file is part of Safe email.

    Safe email is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018 by Marcel Bokhorst (M66B)
*/

import android.Manifest;
import android.arch.lifecycle.Observer;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentSetup extends Fragment {
    private Button btnAccount;
    private ProgressBar pbAccount;
    private TextView tvAccountDone;
    private Button btnAccountManage;

    private Button btnIdentity;
    private ProgressBar pbIdentity;
    private TextView tvIdentityDone;
    private Button btnIdentityManage;

    private Button btnPermissions;
    private TextView tvPermissionsDone;

    private CheckBox cbDarkTheme;

    private ExecutorService executor = Executors.newCachedThreadPool();

    private static final String[] permissions = new String[]{
            Manifest.permission.READ_CONTACTS
    };

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setup, container, false);

        // Get controls
        btnAccount = view.findViewById(R.id.btnAccount);
        pbAccount = view.findViewById(R.id.pbAccount);
        tvAccountDone = view.findViewById(R.id.tvAccountDone);
        btnAccountManage = view.findViewById(R.id.btnAccountManage);

        btnIdentity = view.findViewById(R.id.btnIdentity);
        pbIdentity = view.findViewById(R.id.pbIdentity);
        tvIdentityDone = view.findViewById(R.id.tvIdentityDone);
        btnIdentityManage = view.findViewById(R.id.btnIdentityManage);

        btnPermissions = view.findViewById(R.id.btnPermissions);
        tvPermissionsDone = view.findViewById(R.id.tvPermissionsDone);

        cbDarkTheme = view.findViewById(R.id.cbDarkTheme);

        // Wire controls

        btnAccount.setOnClickListener(new View.OnClickListener() {
            private boolean once;

            @Override
            public void onClick(View view) {
                once = false;
                btnAccount.setEnabled(false);
                pbAccount.setVisibility(View.VISIBLE);

                DB.getInstance(getContext()).account().liveFirstAccount().observe(FragmentSetup.this, new Observer<EntityAccount>() {
                    @Override
                    public void onChanged(@Nullable EntityAccount account) {
                        if (!once) {
                            once = true;

                            Bundle args = new Bundle();
                            if (account != null)
                                args.putLong("id", account.id);

                            FragmentAccount fragment = new FragmentAccount();
                            fragment.setArguments(args);

                            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                            fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("account");
                            fragmentTransaction.commit();

                            btnAccount.setEnabled(true);
                            pbAccount.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });

        btnAccountManage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //getFragmentManager().popBackStack();
                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new FragmentAccounts()).addToBackStack("accounts");
                fragmentTransaction.commit();
            }
        });

        btnIdentity.setOnClickListener(new View.OnClickListener() {
            private boolean once;

            @Override
            public void onClick(View view) {
                once = false;
                btnIdentity.setEnabled(false);
                pbIdentity.setVisibility(View.VISIBLE);

                DB.getInstance(getContext()).identity().liveFirstIdentity().observe(FragmentSetup.this, new Observer<EntityIdentity>() {
                    @Override
                    public void onChanged(@Nullable EntityIdentity identity) {
                        if (!once) {
                            once = true;
                            Bundle args = new Bundle();
                            if (identity != null)
                                args.putLong("id", identity.id);

                            FragmentIdentity fragment = new FragmentIdentity();
                            fragment.setArguments(args);

                            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                            fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("identity");
                            fragmentTransaction.commit();
                        }

                        btnIdentity.setEnabled(true);
                        pbIdentity.setVisibility(View.GONE);
                    }
                });
            }
        });

        btnIdentityManage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //getFragmentManager().popBackStack();
                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new FragmentIdentities()).addToBackStack("identities");
                fragmentTransaction.commit();
            }
        });

        btnPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPermissions(permissions, 1);
            }
        });

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        String theme = prefs.getString("theme", "light");
        boolean dark = "dark".equals(theme);
        cbDarkTheme.setTag(dark);
        cbDarkTheme.setChecked(dark);
        cbDarkTheme.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (checked != (Boolean) button.getTag()) {
                    button.setTag(checked);
                    cbDarkTheme.setChecked(checked);
                    prefs.edit().putString("theme", checked ? "dark" : "light").apply();
                }
            }
        });

        // Initialize

        pbAccount.setVisibility(View.GONE);
        pbIdentity.setVisibility(View.GONE);
        tvAccountDone.setVisibility(View.INVISIBLE);
        tvIdentityDone.setVisibility(View.INVISIBLE);
        tvPermissionsDone.setVisibility(View.INVISIBLE);

        final DB db = DB.getInstance(getContext());

        db.account().liveAccounts(true).observe(this, new Observer<List<EntityAccount>>() {
            @Override
            public void onChanged(@Nullable List<EntityAccount> accounts) {
                tvAccountDone.setVisibility(accounts.size() > 0 ? View.VISIBLE : View.INVISIBLE);
            }
        });

        db.identity().liveIdentities(true).observe(this, new Observer<List<EntityIdentity>>() {
            @Override
            public void onChanged(@Nullable List<EntityIdentity> identities) {
                tvIdentityDone.setVisibility(identities.size() > 0 ? View.VISIBLE : View.INVISIBLE);
            }
        });

        int[] grantResults = new int[permissions.length];
        for (int i = 0; i < permissions.length; i++)
            grantResults[i] = ContextCompat.checkSelfPermission(getActivity(), permissions[i]);

        onRequestPermissionsResult(0, permissions, grantResults);

        // Creat outbox
        executor.submit(new Runnable() {
            @Override
            public void run() {
                EntityFolder outbox = db.folder().getOutbox();
                if (outbox == null) {
                    outbox = new EntityFolder();
                    outbox.name = "OUTBOX";
                    outbox.type = EntityFolder.TYPE_OUTBOX;
                    outbox.synchronize = false;
                    outbox.after = 0;
                    outbox.id = db.folder().insertFolder(outbox);
                }
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(R.string.title_setup);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean has = (grantResults.length > 0);
        for (int result : grantResults)
            if (result != PackageManager.PERMISSION_GRANTED) {
                has = false;
                break;
            }

        btnPermissions.setEnabled(!has);
        tvPermissionsDone.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
    }
}
