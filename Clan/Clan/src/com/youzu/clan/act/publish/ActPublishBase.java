package com.youzu.clan.act.publish;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.kit.app.core.task.DoSomeThing;
import com.kit.imagelib.entity.ImageBean;
import com.kit.utils.AppUtils;
import com.kit.utils.ListUtils;
import com.kit.utils.MessageUtils;
import com.kit.utils.ZogUtils;
import com.lidroid.xutils.task.PriorityAsyncTask;
import com.youzu.android.framework.JsonUtils;
import com.youzu.clan.R;
import com.youzu.clan.app.InjectDo;
import com.youzu.clan.base.TimeCount;
import com.youzu.clan.base.ZBActivity;
import com.youzu.clan.base.callback.JSONCallback;
import com.youzu.clan.base.json.BaseJson;
import com.youzu.clan.base.json.CheckPostJson;
import com.youzu.clan.base.json.UploadJson;
import com.youzu.clan.base.json.act.ActPublishInfo;
import com.youzu.clan.base.json.checkpost.Allowperm;
import com.youzu.clan.base.json.act.ActConfig;
import com.youzu.clan.base.json.model.FileInfo;
import com.youzu.clan.base.net.DoAct;
import com.youzu.clan.base.net.DoCheckPost;
import com.youzu.clan.base.net.ThreadHttp;
import com.youzu.clan.base.util.AppSPUtils;
import com.youzu.clan.base.util.ClanBaseUtils;
import com.youzu.clan.base.util.ClanUtils;
import com.youzu.clan.base.util.StringUtils;
import com.youzu.clan.base.util.ToastUtils;
import com.youzu.clan.base.widget.LoadingDialogFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


public class ActPublishBase extends ZBActivity {
    public static final int UPLOAD_FILE_OK = 1;
    public static final int UPLOAD_FILE_FAIL = 2;
    public static final int UPLOAD_FILES_OK = 3;
    public static final int UPLOAD_FILES_FAIL = 4;

    public ActConfig mActConfig;
    public ActInfo mActInfo = null;

    public CheckPostJson checkPostJson;
    public LinkedHashMap<String, String> attaches;
    public LinkedHashSet<String> attachSet = new LinkedHashSet<>();
    int uploadTimes = 0;
    public String uid, fid;
    public ArrayList<FileInfo> fileInfos = new ArrayList<FileInfo>();
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case UPLOAD_FILE_OK:
                    uploadTimes++;
                    ZogUtils.printError(ActPublishBase.class, "uploadTimes:" + uploadTimes);
                    if (uploadTimes >= fileInfos.size()) {
                        uploadTimes = 0;
                        gotoSend();
                    }
                    break;

                case UPLOAD_FILE_FAIL:
                    sendFail(msg.obj.toString());
                    break;
                case UPLOAD_FILES_FAIL:
                    sendFail(getString(R.string.upload_fail));
                    break;
                case DoAct.SEND_PUBLISH_OK:
                    LoadingDialogFragment.getInstance(ActPublishBase.this).dismissAllowingStateLoss();
                    AppUtils.delay(500, new DoSomeThing() {
                        @Override
                        public void execute(Object... objects) {
                            ToastUtils.show(ActPublishBase.this, R.string.z_act_publish_toast_send_success);
                            Intent intent = new Intent();
                            setResult(Activity.RESULT_OK, intent);
                            finish();
                        }
                    });
                    break;
                case DoAct.SEND_FAIL:
                    String messagestr = "";
                    if (msg != null && msg.obj != null) {
                        messagestr = (String) msg.obj;
                    }
                    ZogUtils.printError(ActPublishBase.class, messagestr);
                    sendFail(TextUtils.isEmpty(messagestr)
                            ? getString(R.string.reply_fail) : messagestr);
                    break;

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getIntent() != null) {
            mActConfig = (ActConfig) getIntent().getSerializableExtra("ActConfig");
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        LoadingDialogFragment.getInstance(ActPublishBase.this).dismissAllowingStateLoss();
    }

    public void sendAct() {
        DoCheckPost.getCheckPost(this, mActConfig.fid, new InjectDo<BaseJson>() {
            @Override
            public boolean doSuccess(BaseJson baseJson) {
                checkPostJson = (CheckPostJson) baseJson;
                if (!checkImageWaitingUpload(mActInfo.picImages)) {
                    ZogUtils.printError(ActPublishBase.class, "checkImageWaitingUpload in");
                    new TimeCount(1000, 1000, new DoSomeThing() {
                        @Override
                        public void execute(Object... objects) {
                            send();
                        }
                    }).start();
                } else {
                    send();
                }
                return true;
            }

            @Override
            public boolean doFail(BaseJson baseJson, String tag) {
                ToastUtils.mkShortTimeToast(ActPublishBase.this, getString(R.string.check_post_fail));
                return true;
            }
        });
    }

    private void send() {        //???????????????????????????????????? ??????????????????
        if (attachSet != null) {
            attachSet.clear();
        }
        fileInfos.clear();
        uid = AppSPUtils.getUid(this);
        LoadingDialogFragment.getInstance(ActPublishBase.this).show();
        if (!ListUtils.isNullOrContainEmpty(mActInfo.picImages)) {
            transFile(mActInfo.picImages);
        } else {
            gotoSend();
        }
    }

    private void checkFilePost() {
        if (!ListUtils.isNullOrContainEmpty(fileInfos)) {
            if (checkPostJson == null) {
                DoCheckPost.getCheckPost(this, fid, new InjectDo<BaseJson>() {
                    @Override
                    public boolean doSuccess(BaseJson baseJson) {
                        checkPostJson = (CheckPostJson) baseJson;
                        gotoUploadFile();
                        return true;
                    }

                    @Override
                    public boolean doFail(BaseJson baseJson, String tag) {
                        sendFail(getString(R.string.check_post_fail));
                        return true;
                    }
                });
            } else {
                gotoUploadFile();
            }
        } else {
            gotoSend();
        }
    }

    /**
     * ???????????????
     *
     * @return
     */
    private boolean sendFail(String str) {
        uploadTimes = 0;
        LoadingDialogFragment.getInstance(ActPublishBase.this).dismissAllowingStateLoss();
        attachSet.clear();
        ToastUtils.mkShortTimeToast(ActPublishBase.this, str);
        return true;
    }

    /**
     * ????????????
     */
    public void transFile(final ArrayList<ImageBean> list) {
        new PriorityAsyncTask() {
            @Override
            protected ArrayList<FileInfo> doInBackground(Object[] objects) {
                Looper.prepare();
                ArrayList<FileInfo> fileInfos = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    ImageBean imageBean = list.get(i);
                    File file = new File(imageBean.path);

                    FileInfo fileInfo = FileInfo.transFileInfo(ActPublishBase.this, file, checkPostJson);
                    if (fileInfo != null
                            && fileInfo.getFile() != null
                            && fileInfo.getFileData() != null) {
                        fileInfos.add(fileInfo);
                    }
                }
                return fileInfos;
            }

            @Override
            protected void onPostExecute(Object o) {
                super.onPostExecute(o);
                ArrayList<FileInfo> fileInfos = (ArrayList<FileInfo>) o;
                ActPublishBase.this.fileInfos = fileInfos;
                checkFilePost();
            }
        }.execute();
    }

    public void gotoUploadFile() {
        if (checkPostJson == null) {
            ToastUtils.mkShortTimeToast(ActPublishBase.this, getString(R.string.check_post_fail));
            return;
        }
        Allowperm allowperm = checkPostJson.getVariables().getAllowperm();
        if (allowperm.getAllowReply().equals("1")) {
            //???????????????????????????????????? ??????????????????
            attaches = new LinkedHashMap<String, String>();
            for (int i = 0; i < fileInfos.size(); i++) {
                FileInfo fileInfo = fileInfos.get(i);
                final int position = i;
                ThreadHttp.uploadFile(ActPublishBase.this, uid,
                        fid, allowperm.getUploadHash(), fileInfo,
                        new JSONCallback() {
                            @Override
                            public void onstart(Context cxt) {
                                super.onstart(cxt);
                            }

                            @Override
                            public void onSuccess(Context ctx, String o) {
                                super.onSuccess(ctx, o);
                                UploadJson uploadJson = ClanUtils.parseObject(o, UploadJson.class);

                                if (!uploadJson.getVariables().getCode().equals("0")) {
                                    onFailed(ctx, Integer.parseInt(uploadJson.getVariables().getCode()), uploadJson.getVariables().getMessage());
                                    return;
                                }
                                String attachId = uploadJson.getVariables().getRet().getAId();
                                ZogUtils.printLog(ActPublishBase.class, "attachId:" + attachId);
                                if (mActInfo.coverImage != null && position == fileInfos.size() - 1) {
                                    _activityaid = attachId;
                                    _activityaid_url = uploadJson.getVariables().getRet().getRelative_url();
                                } else {
                                    attaches.put("" + position, attachId);
                                }
                                MessageUtils.sendMessage(handler, UPLOAD_FILE_OK);
                            }

                            @Override
                            public void onFailed(Context cxt, int errorCode, String msg) {
                                super.onFailed(ActPublishBase.this, errorCode, msg);

                                ZogUtils.printError(ActPublishBase.class, "msg msg msg:" + msg);
                                MessageUtils.sendMessage(handler, 0, 0, msg, UPLOAD_FILE_FAIL, null);
                            }
                        });
            }
        }
    }

    private void setAttaches() {
        if (attaches == null || attaches.isEmpty()) {
            return;
        }
        attaches = ClanUtils.getOrder(attaches);
        for (Map.Entry<String, String> entity : attaches.entrySet()) {
            String attachId = entity.getValue();
            if (!StringUtils.isEmptyOrNullOrNullStr(attachId)) {
                attachSet.add(attachId);
            }
        }
    }

    /**
     * ??????????????????????????????????????????
     * false??????????????????
     *
     * @param list
     * @return
     */
    private boolean checkImageWaitingUpload(ArrayList<ImageBean> list) {
        if (list == null) {
            list = new ArrayList<ImageBean>();
        }
        if (mActInfo.coverImage != null) {
            list.add(mActInfo.coverImage);
        }
        if (list.size() == 0) {
            return true;
        }
        Set<String> types = new TreeSet();
        for (ImageBean b : list) {
            String[] nameSplit = b.path.split("\\.");
            ZogUtils.printLog(FileInfo.class, "nameSplit:" + nameSplit.length);
            String filenameWithoutSuffix = nameSplit[0];
            String filetype = nameSplit[1].toLowerCase();
            types.add(filetype);
        }
        Map<String, String> allowupload = ClanBaseUtils.getAllowUpload(checkPostJson);
        int cannot = 0;
        String cannotTypes = "";
        if (allowupload != null && !allowupload.isEmpty()) {
            for (String s : types) {
                if (allowupload.get(s) != null
                        && allowupload.get(s).equals("0")) {
                    cannotTypes = s + "," + cannotTypes;
                    cannot++;
                }
            }
        }
        if (cannot > 0) {
            cannotTypes = StringUtils.trim(cannotTypes, ",");
            String notAllowTypeStr = getString(R.string.not_allow_file_type, cannotTypes);
            ToastUtils.mkToast(ActPublishBase.this, notAllowTypeStr, 3000);
            return false;
        }
        return true;
    }

    private void gotoSend() {
        setAttaches();
        //???????????????????????????????????? ??????????????????
        if (attachSet != null) {//??????????????????????????????????????????????????????
            ZogUtils.printError(ActPublishBase.class, JsonUtils.toJSON(attachSet).toString());
            ZogUtils.printError(ActPublishBase.class, "attachSet.size():" + attachSet.size());
        }
        doSend();
    }

    private String _activityaid = "";
    private String _activityaid_url = "";

    /**
     * ?????????????????????????????? ?????? ?????? ?????????
     */
    private void doSend() {
//        content = ClanUtils.appendContent(this, content);
        ActPublishInfo info = new ActPublishInfo();
        info.fid = mActConfig.fid;
        info.activitycity = mActInfo.place;
        info.activityclass = mActInfo.catalog;
        info.message = mActInfo.desc;
        info.subject = mActInfo.name;
        info.starttimefrom = mActInfo.time;
        info.userfields = mActInfo.fileds;
        info.extfield = mActInfo.extfield;
        if (mActInfo.coverImage != null) {
            info.activityaid = _activityaid;
            info.activityaid_url = _activityaid_url;
        }
        DoAct.send(ActPublishBase.this, handler, info, attachSet);
    }

}
