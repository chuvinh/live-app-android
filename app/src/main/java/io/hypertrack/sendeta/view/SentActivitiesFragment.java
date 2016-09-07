package io.hypertrack.sendeta.view;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

import io.hypertrack.lib.common.model.HTPlace;
import io.hypertrack.lib.common.model.HTTask;
import io.hypertrack.lib.common.model.HTTaskDisplay;
import io.hypertrack.lib.consumer.utils.HTMapUtils;
import io.hypertrack.sendeta.R;
import io.hypertrack.sendeta.adapter.SentActivitiesAdapter;
import io.hypertrack.sendeta.adapter.callback.UserActivitiesOnClickListener;
import io.hypertrack.sendeta.model.ErrorData;
import io.hypertrack.sendeta.model.UserActivitiesListResponse;
import io.hypertrack.sendeta.model.UserActivityDetails;
import io.hypertrack.sendeta.model.UserActivityModel;
import io.hypertrack.sendeta.network.retrofit.ErrorCodes;
import io.hypertrack.sendeta.network.retrofit.SendETAService;
import io.hypertrack.sendeta.network.retrofit.ServiceGenerator;
import io.hypertrack.sendeta.util.HyperTrackTaskUtils;
import io.hypertrack.sendeta.util.NetworkUtils;
import io.hypertrack.sendeta.util.SharedPreferenceManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by piyush on 29/08/16.
 */
public class SentActivitiesFragment extends BaseFragment implements UserActivitiesOnClickListener {
    private RecyclerView inProcessRecyclerView, historyRecyclerView;
    private LinearLayout noDataLayout, inProcessActivitiesHeader, historyActivitiesHeader;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView noDataText;

    private SentActivitiesAdapter inProcessActivitiesAdapter, historyActivitiesAdapter;
    private ArrayList<UserActivityModel> inProcessActivities, historyActivities;
    private Call<UserActivitiesListResponse> inProcessSentActivitiesCall, historySentActivitiesCall;

    private boolean inProcessActivitiesCallCompleted = true, historyActivitiesCallCompleted = true;

    private View.OnClickListener retryListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            getSentActivities();
        }
    };

    private SwipeRefreshLayout.OnRefreshListener swipeRefreshListener = new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            getSentActivities();
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_user_activities, container, false);

        initRetryAndLoader(rootView);

        // Initialize NoDataLayout
        noDataLayout = (LinearLayout) rootView.findViewById(R.id.fragment_activities_no_data);
        noDataText = (TextView) rootView.findViewById(R.id.fragment_activities_no_data_text);
        noDataText.setText(R.string.sent_activities_no_data_text);

        // Initialize SwipeToRefreshLayout
        swipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.fragment_activities_swipe_refresh);
        swipeRefreshLayout.setColorSchemeResources(R.color.colorAccent);
        swipeRefreshLayout.setOnRefreshListener(swipeRefreshListener);

        // Initialize HeaderLayouts
        inProcessActivitiesHeader = (LinearLayout) rootView.findViewById(R.id.activities_in_process);
        historyActivitiesHeader = (LinearLayout) rootView.findViewById(R.id.activities_history);

        // Initialize RecyclerViews
        inProcessRecyclerView = (RecyclerView) rootView.findViewById(R.id.activities_in_process_list);
        inProcessRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        inProcessRecyclerView.setNestedScrollingEnabled(false);

        historyRecyclerView = (RecyclerView) rootView.findViewById(R.id.activities_history_list);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        historyRecyclerView.setNestedScrollingEnabled(false);

        // Initialize Adapters
        inProcessActivitiesAdapter = new SentActivitiesAdapter(getActivity(), inProcessActivities, this);
        inProcessRecyclerView.setAdapter(inProcessActivitiesAdapter);
        historyActivitiesAdapter = new SentActivitiesAdapter(getActivity(), historyActivities, this, true);
        historyRecyclerView.setAdapter(historyActivitiesAdapter);

        inProcessActivities = new ArrayList<>();
        historyActivities = new ArrayList<>();

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        getSentActivities();

        if (historyActivitiesAdapter != null) {
            for (MapView m : historyActivitiesAdapter.getMapViews()) {
                m.onResume();
            }
        }
    }

    private void getSentActivities() {
        if (inProcessActivitiesCallCompleted || historyActivitiesCallCompleted) {
            displayLoader(true);
        }
        hideRetryLayout();
        noDataLayout.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);

        // Initialize SendETAService, if not done already
        SendETAService sendETAService = ServiceGenerator.createService(SendETAService.class,
                SharedPreferenceManager.getUserAuthToken());


        // Initiate inProcessReceivedActivities Call
        if (inProcessActivitiesCallCompleted) {
            inProcessActivitiesCallCompleted = false;

            inProcessSentActivitiesCall = sendETAService.getInProcessSentUserActivities();
            inProcessSentActivitiesCall.enqueue(new Callback<UserActivitiesListResponse>() {
                @Override
                public void onResponse(Call<UserActivitiesListResponse> call, Response<UserActivitiesListResponse> response) {
                    if (historyActivitiesCallCompleted) {
                        displayLoader(false);
                    }

                    if (response.isSuccessful()) {

                        UserActivitiesListResponse activitiesListResponse = response.body();
                        if (activitiesListResponse != null && activitiesListResponse.getUserActivities() != null
                                && !activitiesListResponse.getUserActivities().isEmpty()) {

                            parseUserActivityDetails(activitiesListResponse.getUserActivities(), true);

                            inProcessActivitiesHeader.setVisibility(View.VISIBLE);
                            inProcessRecyclerView.setVisibility(View.VISIBLE);

                            swipeRefreshLayout.setVisibility(View.VISIBLE);
                            noDataLayout.setVisibility(View.GONE);

                            inProcessActivitiesAdapter.setUserActivities(inProcessActivities);

                            inProcessActivitiesCallCompleted = true;
                            return;
                        }

                        // Hide InProcess List Views
                        inProcessActivities.clear();
                        inProcessActivitiesHeader.setVisibility(View.GONE);
                        inProcessRecyclerView.setVisibility(View.GONE);

                        checkForNoData();

                        inProcessActivitiesCallCompleted = true;
                        return;
                    }

                    showRetryLayout(getString(R.string.generic_error_message), retryListener);
                    inProcessActivitiesCallCompleted = true;
                }

                @Override
                public void onFailure(Call<UserActivitiesListResponse> call, Throwable t) {
                    inProcessActivitiesCallCompleted = true;
                    if (historyActivitiesCallCompleted) {
                        displayLoader(false);
                    }

                    ErrorData errorData = new ErrorData();
                    try {
                        errorData = NetworkUtils.processFailure(t);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    showErrorMessage(errorData);
                }

                private void showErrorMessage(ErrorData errorData) {
                    if (getActivity() == null || getActivity().isFinishing())
                        return;

                    if (ErrorCodes.NO_INTERNET.equalsIgnoreCase(errorData.getCode()) ||
                            ErrorCodes.REQUEST_TIMED_OUT.equalsIgnoreCase(errorData.getCode())) {
                        showRetryLayout(getString(R.string.network_issue), retryListener);

                    } else {
                        showRetryLayout(getString(R.string.generic_error_message), retryListener);
                    }
                }
            });
        }

        // Initiate historySentActivities Call
        if (historyActivitiesCallCompleted) {
            historyActivitiesCallCompleted = false;

            historySentActivitiesCall = sendETAService.getHistorySentUserActivities();
            historySentActivitiesCall.enqueue(new Callback<UserActivitiesListResponse>() {
                @Override
                public void onResponse(Call<UserActivitiesListResponse> call, Response<UserActivitiesListResponse> response) {
                    if (inProcessActivitiesCallCompleted) {
                        displayLoader(false);
                    }

                    if (response.isSuccessful()) {

                        UserActivitiesListResponse activitiesListResponse = response.body();
                        if (activitiesListResponse != null && activitiesListResponse.getUserActivities() != null
                                && !activitiesListResponse.getUserActivities().isEmpty()) {

                            parseUserActivityDetails(activitiesListResponse.getUserActivities(), false);

                            historyActivitiesHeader.setVisibility(View.VISIBLE);
                            historyRecyclerView.setVisibility(View.VISIBLE);

                            swipeRefreshLayout.setVisibility(View.VISIBLE);
                            noDataLayout.setVisibility(View.GONE);

                            historyActivitiesAdapter.setUserActivities(historyActivities);

                            historyActivitiesCallCompleted = true;
                            return;
                        }

                        // Hide InProcess List Views
                        historyActivities.clear();
                        historyActivitiesHeader.setVisibility(View.GONE);
                        historyRecyclerView.setVisibility(View.GONE);

                        checkForNoData();

                        historyActivitiesCallCompleted = true;
                        return;
                    }

                    showRetryLayout(getString(R.string.generic_error_message), retryListener);
                    historyActivitiesCallCompleted = true;
                }

                @Override
                public void onFailure(Call<UserActivitiesListResponse> call, Throwable t) {
                    historyActivitiesCallCompleted = true;
                    if (inProcessActivitiesCallCompleted) {
                        displayLoader(false);
                    }

                    ErrorData errorData = new ErrorData();
                    try {
                        errorData = NetworkUtils.processFailure(t);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    showErrorMessage(errorData);
                }

                private void showErrorMessage(ErrorData errorData) {
                    if (getActivity() == null || getActivity().isFinishing())
                        return;

                    if (ErrorCodes.NO_INTERNET.equalsIgnoreCase(errorData.getCode()) ||
                            ErrorCodes.REQUEST_TIMED_OUT.equalsIgnoreCase(errorData.getCode())) {
                        showRetryLayout(getString(R.string.network_issue), retryListener);

                    } else {
                        showRetryLayout(getString(R.string.generic_error_message), retryListener);
                    }
                }
            });
        }
    }

    private void checkForNoData() {
        if ((inProcessActivitiesCallCompleted && inProcessActivities.isEmpty())
                && (historyActivitiesCallCompleted && historyActivities.isEmpty())) {
            noDataLayout.setVisibility(View.VISIBLE);
        }
    }

    private void parseUserActivityDetails(ArrayList<UserActivityDetails> userActivityDetailsDetails, boolean inProcess) {

        Activity context = getActivity();

        if (context != null && !context.isFinishing() && userActivityDetailsDetails != null) {

            if (inProcess) {
                inProcessActivities.clear();
            } else {
                historyActivities.clear();
            }

            // Parse all UserActivityDetails fetched from server
            for (UserActivityDetails userActivityDetails : userActivityDetailsDetails) {

                UserActivityModel userActivity = new UserActivityModel();
                userActivity.setInProcess(inProcess);

                // Get TaskDetails from UserActivityDetails
                HTTask task = userActivityDetails.getTaskDetails();
                if (task != null) {

                    // Get TaskID
                    userActivity.setTaskID(task.getId());

                    // Get Activity Title
                    if (task.getTaskDisplay() != null) {
                        Integer resId = HyperTrackTaskUtils.getTaskDisplayStatus(task.getTaskDisplay());
                        if (resId != null) {
                            userActivity.setTitle(context.getString(resId));
                        } else if (!TextUtils.isEmpty(task.getTaskDisplay().getStatusText())) {
                            userActivity.setTitle(task.getTaskDisplay().getStatusText());
                        }
                    }

                    // Get Activity MainIcon
                    if (!TextUtils.isEmpty(task.getStatus())) {
                        String taskStatus = task.getStatus();
                        switch (taskStatus) {
                            case HTTask.TASK_STATUS_CANCELED:
                            case HTTask.TASK_STATUS_ABORTED:
                            case HTTask.TASK_STATUS_SUSPENDED:
                                userActivity.setDisabledMainIcon(false);
                                break;
                        }
                    }

                    // Get Activity EndAddress
                    HTPlace destination = task.getDestination();
                    if (destination != null && !TextUtils.isEmpty(destination.getAddress())) {
                        userActivity.setEndAddress(destination.getAddress());
                    }

                    if (inProcess) {
                        // Get Activity Subtitle
                        HTTaskDisplay taskDisplay = task.getTaskDisplay();
                        if (taskDisplay != null) {
                            String formattedTime = HyperTrackTaskUtils.getFormattedTimeString(context,
                                    HyperTrackTaskUtils.getTaskDisplayETA(taskDisplay));
                            if (!TextUtils.isEmpty(formattedTime)) {
                                userActivity.setSubtitle(formattedTime + " away");
                            }
                        }
                    } else {

                        // Get Activity Subtitle
                        String formattedSubtitle = HyperTrackTaskUtils.getFormattedTaskDurationAndDistance(context, task);
                        if (!TextUtils.isEmpty(formattedSubtitle)) {
                            userActivity.setSubtitle(formattedSubtitle);
                        }

                        // Get Activity Date
                        String formattedDate = HyperTrackTaskUtils.getTaskDateString(task);
                        if (!TextUtils.isEmpty(formattedDate)) {
                            userActivity.setDate(formattedDate);
                        }

                        // Get Activity StartAddress
                        String startLocationString =
                                task.getStartLocation() != null ? task.getStartLocation().getDisplayString() : null;
                        if (!TextUtils.isEmpty(startLocationString)) {
                            userActivity.setStartAddress(startLocationString);
                        }

                        // Get Activity StartTime
                        if (!TextUtils.isEmpty(task.getTaskStartTimeDisplayString())) {
                            userActivity.setStartTime(task.getTaskStartTimeDisplayString());
                        }

                        // Get Activity EndTime
                        if (!TextUtils.isEmpty(task.getTaskEndTimeDisplayString())) {
                            userActivity.setEndTime(task.getTaskEndTimeDisplayString());
                        }

                        // Get Completion Location
                        if (destination != null && destination.getLocation() != null) {
                            double[] coordinates = task.getDestination().getLocation().getCoordinates();
                            if (coordinates[0] != 0.0 && coordinates[1] != 0.0) {
                                userActivity.setEndLocation(new LatLng(coordinates[1], coordinates[0]));
                            }
                        }

                        // Get Start Location
                        if (task.getStartLocation() != null) {
                            double[] coordinates = task.getStartLocation().getCoordinates();
                            if (coordinates[0] != 0.0 && coordinates[1] != 0.0) {
                                userActivity.setStartLocation(new LatLng(coordinates[1], coordinates[0]));
                            }
                        }

                        // Get Polyline
                        String encodedPolyline = task.getEncodedPolyline();
                        if (!TextUtils.isEmpty(encodedPolyline)) {
                            List<LatLng> polyline = HTMapUtils.decode(encodedPolyline);
                            if (polyline != null && !polyline.isEmpty()) {
                                userActivity.setPolyline(polyline);
                            }
                        }
                    }

                    if (inProcess) {
                        inProcessActivities.add(userActivity);
                    } else {
                        historyActivities.add(userActivity);
                    }
                }
            }
        }
    }

    @Override
    public void OnInProcessActivityClicked(int position, UserActivityModel inProcessActivity) {
        if (inProcessActivity == null || TextUtils.isEmpty(inProcessActivity.getTaskID()))
            return;

        ArrayList<String> taskIDList = new ArrayList<>();
        taskIDList.add(inProcessActivity.getTaskID());

        Intent trackTaskIntent = new Intent(getActivity(), Track.class);
        trackTaskIntent.putStringArrayListExtra(Track.KEY_TASK_ID_LIST, taskIDList);
        startActivity(trackTaskIntent);
    }

    @Override
    public void OnHistoryActivityClicked(int position, UserActivityModel historyActivity) {
        if (historyActivity == null || TextUtils.isEmpty(historyActivity.getTaskID()))
            return;

        ArrayList<String> taskIDList = new ArrayList<>();
        taskIDList.add(historyActivity.getTaskID());

        Intent trackTaskIntent = new Intent(getActivity(), Track.class);
        trackTaskIntent.putStringArrayListExtra(Track.KEY_TASK_ID_LIST, taskIDList);
        startActivity(trackTaskIntent);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        if (historyActivitiesAdapter != null) {
            for (MapView m : historyActivitiesAdapter.getMapViews()) {
                m.onLowMemory();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (historyActivitiesAdapter != null) {
            for (MapView m : historyActivitiesAdapter.getMapViews()) {
                m.onPause();
            }
        }
    }

    @Override
    public void onDestroy() {
        if (historyActivitiesAdapter != null) {
            for (MapView m : historyActivitiesAdapter.getMapViews()) {
                m.onDestroy();
            }
        }

        if (inProcessSentActivitiesCall != null) {
            inProcessSentActivitiesCall.cancel();
        }

        if (historySentActivitiesCall != null) {
            historySentActivitiesCall.cancel();
        }

        super.onDestroy();
    }
}