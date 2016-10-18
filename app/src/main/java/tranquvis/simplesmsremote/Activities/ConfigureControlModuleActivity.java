package tranquvis.simplesmsremote.Activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tranquvis.simplesmsremote.Adapters.GrantedPhonesEditableListAdapter;
import tranquvis.simplesmsremote.CommandManagement.ControlModule;
import tranquvis.simplesmsremote.Data.ControlModuleUserData;
import tranquvis.simplesmsremote.Data.DataManager;
import tranquvis.simplesmsremote.Data.ModuleSettingsData;
import tranquvis.simplesmsremote.Utils.PermissionUtils;
import tranquvis.simplesmsremote.R;

public class ConfigureControlModuleActivity extends AppCompatActivity implements View.OnClickListener
{
    private static final int REQUEST_CODE_PERM_MODULE_REQUIREMENTS = 1;

    private ControlModule controlModule;
    private ControlModuleUserData userData;
    protected ModuleSettingsData moduleSettings;
    private List<String> grantedPhones;
    private boolean isModuleEnabled;
    private boolean saveOnStop = true;

    private String[] remainingPermissionRequests;
    private String[] lastPermissionRequests;
    private boolean processPermissionRequestOnResume = false;

    private ListView grantedPhonesListView;
    private GrantedPhonesEditableListAdapter grantedPhonesListAdapter;

    private CoordinatorLayout coordinatorLayout;
    private ViewStub settingsViewStub;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure_control_module);
        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Resources res = getResources();

        String controlModuleId = getIntent().getStringExtra("controlActionId");
        controlModule = ControlModule.getFromId(controlModuleId);
        if(controlModule == null)
        {
            finish();
            return;
        }
        userData = controlModule.getUserData();
        isModuleEnabled = controlModule.isEnabled();

        toolbar.setTitle(R.string.title_activity_configure_control_action);

        if(controlModule.getTitleRes() != -1)
        {
            toolbar.setSubtitle(controlModule.getTitleRes());
        }

        if(controlModule.getDescriptionRes() != -1)
        {
            ((TextView) findViewById(R.id.textView_description)).setText(
                    controlModule.getDescriptionRes());
        }

        ((TextView)findViewById(R.id.textView_commands)).setText(controlModule.getCommandsString());

        if(controlModule.getParamInfoRes() != -1)
        {
            ((TextView)findViewById(R.id.textView_command_parameter_info))
                    .setText(controlModule.getParamInfoRes());
        }
        else
        {
            findViewById(R.id.textView_command_parameter_info_title).setVisibility(View.GONE);
            findViewById(R.id.textView_command_parameter_info).setVisibility(View.GONE);
        }

        TextView compatibilityTextView = (TextView)findViewById(R.id.textView_compatibility_info);
        Button buttonChangeEnabled = (Button)findViewById(R.id.button_change_enabled);

        buttonChangeEnabled.setText(!isModuleEnabled ? R.string.enable_module
                : R.string.disable_module);

        findViewById(R.id.imageButton_command_info).setOnClickListener(this);

        if(controlModule.isCompatible())
        {
            compatibilityTextView.setText(R.string.compatible);
            compatibilityTextView.setTextColor(res.getColor(R.color.colorSuccess));
            buttonChangeEnabled.setOnClickListener(this);
        }
        else
        {
            compatibilityTextView.setText(R.string.incompatible);
            compatibilityTextView.setTextColor(res.getColor(R.color.colorError));
            buttonChangeEnabled.setEnabled(false);
        }

        if(isModuleEnabled)
        {
            grantedPhones = userData.getGrantedPhones();
            if (grantedPhones.isEmpty())
                grantedPhones.add("");

            grantedPhonesListView = (ListView) findViewById(R.id.listView_granted_phones);
            grantedPhonesListAdapter = new GrantedPhonesEditableListAdapter(this, grantedPhones,
                    grantedPhonesListView);
            grantedPhonesListView.setScrollContainer(false);
            grantedPhonesListView.setAdapter(grantedPhonesListAdapter);

            FloatingActionButton addPhoneFab = (FloatingActionButton) findViewById(R.id.fab_add_phone);
            addPhoneFab.setOnClickListener(this);

            moduleSettings = userData.getSettings();
        }
        else
        {
            findViewById(R.id.layout_user_data).setVisibility(View.GONE);
            findViewById(R.id.textView_user_data_title).setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case android.R.id.home:
                if(isModuleEnabled && saveOnStop)
                {
                    saveUserData();
                    saveOnStop = false;
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.button_change_enabled:
                if(isModuleEnabled)
                    disableModule();
                else
                    enableModule();
                break;
            case R.id.fab_add_phone:
                grantedPhonesListAdapter.addPhone("");
                break;
            case R.id.imageButton_command_info:
                startActivity(new Intent(this, HelpHowToControlActivity.class));
                break;
        }
    }

    private void enableModule()
    {
        if (!PermissionUtils.AppHasPermissions(this,
                controlModule.getRequiredPermissions(this)))
            requestPermissions(controlModule.getRequiredPermissions(this));
        else
        {
            saveUserData();
            recreate();
        }
    }

    private void disableModule()
    {
        new AlertDialog.Builder(this)
                .setMessage(R.string.alert_sure_to_disable_module)
                .setNegativeButton(R.string.simple_no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i){}
                        })
                .setPositiveButton(R.string.simple_yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i)
                            {
                                DataManager.getUserData().removeControlModule(
                                        controlModule.getId());
                                isModuleEnabled = false;
                                try
                                {
                                    DataManager.SaveUserData(ConfigureControlModuleActivity.this);
                                    Toast.makeText(ConfigureControlModuleActivity.this,
                                            R.string.control_module_disabled_successful,
                                            Toast.LENGTH_SHORT).show();
                                } catch (IOException e){
                                    Toast.makeText(ConfigureControlModuleActivity.this,
                                            R.string.alert_save_data_failed,
                                            Toast.LENGTH_SHORT).show();
                                }

                                recreate();
                            }
                        })
                .show();
    }

    private void requestPermissions(String[] permissions)
    {
        PermissionUtils.RequestResult result = PermissionUtils.RequestNextPermissions(this,
                permissions, REQUEST_CODE_PERM_MODULE_REQUIREMENTS);
        remainingPermissionRequests = result.getRemainingPermissions();
        lastPermissionRequests = result.getRequestPermissions();
        if(result.getRequestType() == PermissionUtils.RequestType.INDEPENDENT_ACTIVITY)
            processPermissionRequestOnResume = true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        switch (requestCode)
        {
            case REQUEST_CODE_PERM_MODULE_REQUIREMENTS:
                onModuleRequiredPermissionRequestFinished();
                break;
        }
    }

    @Override
    protected void onPostResume()
    {
        super.onPostResume();

        if(processPermissionRequestOnResume)
        {
            processPermissionRequestOnResume = false;
            onModuleRequiredPermissionRequestFinished();
        }
    }

    private void onModuleRequiredPermissionRequestFinished()
    {
        if(PermissionUtils.AppHasPermissions(this, lastPermissionRequests))
        {
            if(remainingPermissionRequests != null && remainingPermissionRequests.length > 0)
                requestPermissions(remainingPermissionRequests);
            else
            {
                //all permissions granted
                enableModule();
            }
        }
        else
        {
            Snackbar.make(coordinatorLayout, R.string.permissions_denied, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop()
    {
        if(isModuleEnabled && saveOnStop)
            saveUserData();
        super.onStop();
    }

    private void saveUserData()
    {
        if(isModuleEnabled) {
            grantedPhonesListAdapter.updateData();
            List<String> filteredPhones = new ArrayList<>();
            for(String phone : grantedPhones)
            {
                phone = phone.trim();
                if(!phone.isEmpty() && !filteredPhones.contains(phone))
                    filteredPhones.add(phone);
            }
            DataManager.getUserData().setControlModule(new ControlModuleUserData(
                    controlModule.getId(), grantedPhones, moduleSettings));
        }
        else
            DataManager.getUserData().addControlModule(new ControlModuleUserData(
                    controlModule.getId(), new ArrayList<String>(), moduleSettings));

        try
        {
            DataManager.SaveUserData(this);
            if(!isModuleEnabled)
                Toast.makeText(this, R.string.control_module_enabled_successful, Toast.LENGTH_SHORT)
                    .show();
        } catch (IOException e)
        {
            Toast.makeText(this, R.string.alert_save_data_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    protected void setSettingsContentLayout(int layoutId)
    {
        ViewStub settingsViewStub = (ViewStub) findViewById(R.id.viewStub_settings_content);
        settingsViewStub.setLayoutResource(layoutId);
        settingsViewStub.inflate();
    }

    protected CoordinatorLayout getCoordinatorLayout()
    {
        return coordinatorLayout;
    }

    protected ControlModule getControlModule()
    {
        return controlModule;
    }

    protected ControlModuleUserData getUserData()
    {
        return userData;
    }
}
