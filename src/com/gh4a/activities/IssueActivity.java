/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.gh4a.BaseActivity;
import com.gh4a.Gh4Application;
import com.gh4a.ProgressDialogTask;
import com.gh4a.R;
import com.gh4a.adapter.IssueEventAdapter;
import com.gh4a.fragment.CommentBoxFragment;
import com.gh4a.fragment.IssueFragment;
import com.gh4a.loader.IsCollaboratorLoader;
import com.gh4a.loader.IssueCommentListLoader;
import com.gh4a.loader.IssueEventHolder;
import com.gh4a.loader.IssueLoader;
import com.gh4a.loader.LoaderCallbacks;
import com.gh4a.loader.LoaderResult;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.AvatarHandler;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.StringUtils;
import com.gh4a.utils.UiUtils;
import com.gh4a.widget.DividerItemDecoration;
import com.gh4a.widget.IssueLabelSpan;
import com.gh4a.widget.IssueStateTrackingFloatingActionButton;
import com.gh4a.widget.SwipeRefreshLayout;
import com.github.mobile.util.HtmlUtils;
import com.github.mobile.util.HttpImageGetter;

import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.service.IssueService;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class IssueActivity extends BaseActivity implements View.OnClickListener {
    public static Intent makeIntent(Context context, String login, String repoName, int number) {
        return makeIntent(context, login, repoName, number, -1);
    }
    public static Intent makeIntent(Context context, String login, String repoName,
            int number, long initialCommentId) {
        return new Intent(context, IssueActivity.class)
                .putExtra("owner", login)
                .putExtra("repo", repoName)
                .putExtra("number", number)
                .putExtra("initial_comment", initialCommentId);
    }

    private static final int REQUEST_EDIT_ISSUE = 1000;

    private Issue mIssue;
    private String mRepoOwner;
    private String mRepoName;
    private int mIssueNumber;
    private long mInitialCommentId;
    private ViewGroup mHeader;
    private Boolean mIsCollaborator;
    private IssueStateTrackingFloatingActionButton mEditFab;
    private final Handler mHandler = new Handler();
    private IssueFragment mFragment;

    private final LoaderCallbacks<Issue> mIssueCallback = new LoaderCallbacks<Issue>(this) {
        @Override
        protected Loader<LoaderResult<Issue>> onCreateLoader() {
            return new IssueLoader(IssueActivity.this, mRepoOwner, mRepoName, mIssueNumber);
        }
        @Override
        protected void onResultReady(Issue result) {
            mIssue = result;
            showUiIfDone();
            supportInvalidateOptionsMenu();
        }
    };

    private final LoaderCallbacks<Boolean> mCollaboratorCallback = new LoaderCallbacks<Boolean>(this) {
        @Override
        protected Loader<LoaderResult<Boolean>> onCreateLoader() {
            return new IsCollaboratorLoader(IssueActivity.this, mRepoOwner, mRepoName);
        }
        @Override
        protected void onResultReady(Boolean result) {
            mIsCollaborator = result;
            showUiIfDone();
            supportInvalidateOptionsMenu();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.frame_layout);
        setContentShown(false);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getString(R.string.issue) + " #" + mIssueNumber);
        actionBar.setSubtitle(mRepoOwner + "/" + mRepoName);
        actionBar.setDisplayHomeAsUpEnabled(true);

        LayoutInflater inflater = getLayoutInflater();

        mHeader = (ViewGroup) inflater.inflate(R.layout.issue_header, null);
        mHeader.setClickable(false);
        mHeader.setVisibility(View.GONE);
        addHeaderView(mHeader, false);

        setFragment((IssueFragment) getSupportFragmentManager().findFragmentById(R.id.details));

        setToolbarScrollable(true);

        getSupportLoaderManager().initLoader(0, null, mIssueCallback);
        getSupportLoaderManager().initLoader(1, null, mCollaboratorCallback);
    }

    @Override
    protected void onInitExtras(Bundle extras) {
        super.onInitExtras(extras);
        mRepoOwner = extras.getString("owner");
        mRepoName = extras.getString("repo");
        mIssueNumber = extras.getInt("number");
        mInitialCommentId = extras.getLong("initial_comment", -1);
        extras.remove("initial_comment");
    }

    private void showUiIfDone() {
        if (mIssue == null || mIsCollaborator == null) {
            return;
        }
        setFragment(IssueFragment.newInstance(mRepoOwner, mRepoName,
                mIssue, mIsCollaborator, mInitialCommentId));
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.details, mFragment)
                .commitAllowingStateLoss();
        mInitialCommentId = -1;

        updateHeader();
        updateFabVisibility();
        setContentShown(true);
    }

    private void setFragment(IssueFragment fragment) {
        mFragment = fragment;
        setChildScrollDelegate(fragment);
    }

    private void updateHeader() {
        TextView tvState = (TextView) mHeader.findViewById(R.id.tv_state);
        boolean closed = ApiHelpers.IssueState.CLOSED.equals(mIssue.getState());
        int stateTextResId = closed ? R.string.closed : R.string.open;
        int stateColorAttributeId = closed ? R.attr.colorIssueClosed : R.attr.colorIssueOpen;

        tvState.setText(getString(stateTextResId).toUpperCase(Locale.getDefault()));
        transitionHeaderToColor(stateColorAttributeId,
                closed ? R.attr.colorIssueClosedDark : R.attr.colorIssueOpenDark);

        TextView tvTitle = (TextView) mHeader.findViewById(R.id.tv_title);
        tvTitle.setText(mIssue.getTitle());

        mHeader.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.issue_menu, menu);

        boolean authorized = Gh4Application.get().isAuthorized();
        boolean isCreator = mIssue != null && authorized &&
                ApiHelpers.loginEquals(mIssue.getUser(), Gh4Application.get().getAuthLogin());
        boolean isClosed = mIssue != null && ApiHelpers.IssueState.CLOSED.equals(mIssue.getState());
        boolean isCollaborator = mIsCollaborator != null && mIsCollaborator;
        boolean closerIsCreator = mIssue != null
                && ApiHelpers.userEquals(mIssue.getUser(), mIssue.getClosedBy());
        boolean canClose = mIssue != null && authorized && (isCreator || isCollaborator);
        boolean canOpen = canClose && (isCollaborator || closerIsCreator);

        if (!canClose || isClosed) {
            menu.removeItem(R.id.issue_close);
        }
        if (!canOpen || !isClosed) {
            menu.removeItem(R.id.issue_reopen);
        }

        if (mIssue == null) {
            menu.removeItem(R.id.share);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected Intent navigateUp() {
        return IssueListActivity.makeIntent(this, mRepoOwner, mRepoName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.issue_close:
            case R.id.issue_reopen:
                if (checkForAuthOrExit()) {
                    new IssueOpenCloseTask(itemId == R.id.issue_reopen).schedule();
                }
                return true;
            case R.id.share:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_issue_subject,
                        mIssueNumber, mIssue.getTitle(), mRepoOwner + "/" + mRepoName));
                shareIntent.putExtra(Intent.EXTRA_TEXT,  mIssue.getHtmlUrl());
                shareIntent = Intent.createChooser(shareIntent, getString(R.string.share_title));
                startActivity(shareIntent);
                return true;
            case R.id.browser:
                IntentUtils.launchBrowser(this, Uri.parse(mIssue.getHtmlUrl()));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        mIssue = null;
        mIsCollaborator = null;
        setContentShown(false);

        transitionHeaderToColor(R.attr.colorPrimary, R.attr.colorPrimaryDark);
        mHeader.setVisibility(View.GONE);

        if (mFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(mFragment)
                    .commit();
            setFragment(null);
        }

        // onRefresh() can be triggered in the draw loop, and CoordinatorLayout doesn't
        // like its child list being changed while drawing
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                updateFabVisibility();
            }
        });

        forceLoaderReload(0, 1);
        super.onRefresh();
    }

    private void updateFabVisibility() {
        boolean isIssueOwner = mIssue != null
                && ApiHelpers.loginEquals(mIssue.getUser(), Gh4Application.get().getAuthLogin());
        boolean isCollaborator = mIsCollaborator != null && mIsCollaborator;
        boolean shouldHaveFab = (isIssueOwner || isCollaborator) && mIssue != null;
        CoordinatorLayout rootLayout = getRootLayout();

        if (shouldHaveFab && mEditFab == null) {
            mEditFab = (IssueStateTrackingFloatingActionButton)
                    getLayoutInflater().inflate(R.layout.issue_edit_fab, rootLayout, false);
            mEditFab.setOnClickListener(this);
            rootLayout.addView(mEditFab);
        } else if (!shouldHaveFab && mEditFab != null) {
            rootLayout.removeView(mEditFab);
            mEditFab = null;
        }
        if (mEditFab != null) {
            mEditFab.setState(mIssue.getState());
        }
    }

    private boolean checkForAuthOrExit() {
        if (Gh4Application.get().isAuthorized()) {
            return true;
        }
        Intent intent = new Intent(this, Github4AndroidActivity.class);
        startActivity(intent);
        finish();
        return false;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.edit_fab && checkForAuthOrExit()) {
            Intent editIntent = IssueEditActivity.makeEditIntent(this,
                    mRepoOwner, mRepoName, mIssue);
            startActivityForResult(editIntent, REQUEST_EDIT_ISSUE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EDIT_ISSUE) {
            if (resultCode == Activity.RESULT_OK) {
                forceLoaderReload(0);
                setResult(RESULT_OK);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private class IssueOpenCloseTask extends ProgressDialogTask<Issue> {
        private final boolean mOpen;

        public IssueOpenCloseTask(boolean open) {
            super(IssueActivity.this, 0, open ? R.string.opening_msg : R.string.closing_msg);
            mOpen = open;
        }

        @Override
        protected ProgressDialogTask<Issue> clone() {
            return new IssueOpenCloseTask(mOpen);
        }

        @Override
        protected Issue run() throws IOException {
            IssueService issueService = (IssueService)
                    Gh4Application.get().getService(Gh4Application.ISSUE_SERVICE);
            RepositoryId repoId = new RepositoryId(mRepoOwner, mRepoName);

            Issue issue = issueService.getIssue(repoId, mIssueNumber);
            issue.setState(mOpen ? ApiHelpers.IssueState.OPEN : ApiHelpers.IssueState.CLOSED);

            return issueService.editIssue(repoId, issue);
        }

        @Override
        protected void onSuccess(Issue result) {
            mIssue = result;

            updateHeader();
            if (mEditFab != null) {
                mEditFab.setState(mIssue.getState());
            }
            if (mFragment != null) {
                mFragment.updateState(mIssue);
            }
            setResult(RESULT_OK);
            supportInvalidateOptionsMenu();
        }

        @Override
        protected String getErrorMessage() {
            @StringRes int messageResId = mOpen
                    ? R.string.issue_error_reopen : R.string.issue_error_close;
            return getContext().getString(messageResId, mIssueNumber);
        }
    }
}
