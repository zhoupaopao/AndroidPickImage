package org.jay.androidpickimage.multi.postimage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.jay.androidpickimage.R;
import org.jay.androidpickimage.application.MyApplication;
import org.jay.androidpickimage.helper.PickImageHelper;
import org.jay.androidpickimage.module.ImageModule;
import org.jay.androidpickimage.multi.multi_image_selector.MultiImageSelector;
import org.jay.androidpickimage.multi.multi_image_selector.MultiImageSelectorActivity;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.datatype.BmobFile;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.QueryListener;
import cn.bmob.v3.listener.UpdateListener;
import cn.bmob.v3.listener.UploadBatchListener;

public class PostImagesActivity extends AppCompatActivity {

    public static final int IMAGE_SIZE = 9;
    private static final int REQUEST_IMAGE = 1002;
    @BindView(R.id.btn)
    Button mBtn;
    @BindView(R.id.et_content)
    EditText mEtContent;
    @BindView(R.id.rcv_img)
    RecyclerView rcvImg;
    @BindView(R.id.tv)
    TextView tv;
    @BindView(R.id.progress)
    ProgressBar mProgress;

    private List<String> originImages;//原始图片
    private List<String> dragImages;//压缩长宽后图片
    private Context mContext;
    private PostArticleImgAdapter postArticleImgAdapter;
    private ItemTouchHelper itemTouchHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_images);
        ButterKnife.bind(this);
        initData();
        initView();
//        getImages();
    }
    private void getImages() {
        mProgress.setVisibility(View.VISIBLE);
        BmobQuery<ImageModule> bmobQuery=new BmobQuery<>();
        bmobQuery.getObject(getString(R.string.avatar_id), new QueryListener<ImageModule>() {
            @Override
            public void done(ImageModule imageModule, BmobException e) {
                mProgress.setVisibility(View.GONE);
                if(e==null){
                    Toast.makeText(mContext, ""+imageModule.getUrls().size(), Toast.LENGTH_SHORT).show();
                }else{
                    Log.d("jay", "done: [imageModule, e]="+e.toString());
                }
            }
        });
    }
    private void initData() {
        if (originImages == null) {
            originImages = new ArrayList<>();
        }
        mContext = getApplicationContext();
        //添加按钮图片资源
        String plusPath = getString(R.string.glide_plus_icon_string) + getPackageInfo(mContext).packageName + "/mipmap/" + R.mipmap.mine_btn_plus;
        dragImages = new ArrayList<>();
        originImages.add(plusPath);//添加按键，超过9张时在adapter中隐藏
        dragImages.addAll(originImages);
        new Thread(new MyRunnable(this, dragImages, originImages, dragImages, myHandler, false)).start();//开启线程，在新线程中去压缩图片
    }

    public static PackageInfo getPackageInfo(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
        }
        return new PackageInfo();
    }

    private void initView() {
        rcvImg = (RecyclerView) findViewById(R.id.rcv_img);
        tv = (TextView) findViewById(R.id.tv);
        initRcv();
    }

    private void initRcv() {

        postArticleImgAdapter = new PostArticleImgAdapter(mContext, dragImages);
        rcvImg.setLayoutManager(new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL));
        rcvImg.setAdapter(postArticleImgAdapter);
        MyCallBack myCallBack = new MyCallBack(postArticleImgAdapter, dragImages, originImages);
        itemTouchHelper = new ItemTouchHelper(myCallBack);
        itemTouchHelper.attachToRecyclerView(rcvImg);//绑定RecyclerView

        //事件监听
        rcvImg.addOnItemTouchListener(new OnRecyclerItemClickListener(rcvImg) {

            @Override
            public void onItemClick(RecyclerView.ViewHolder vh) {
                if (originImages.get(vh.getAdapterPosition()).contains(getString(R.string.glide_plus_icon_string))) {//打开相册
                    MultiImageSelector.create()
                            .showCamera(true)
                            .count(IMAGE_SIZE - originImages.size() + 1)
                            .multi()
                            .start(PostImagesActivity.this, REQUEST_IMAGE);
                } else {
                    Toast.makeText(mContext, "Review", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onItemLongClick(RecyclerView.ViewHolder vh) {
                //如果item不是最后一个，则执行拖拽
                if (vh.getLayoutPosition() != dragImages.size() - 1) {
                    itemTouchHelper.startDrag(vh);
                }
            }
        });

        myCallBack.setDragListener(new MyCallBack.DragListener() {
            @Override
            public void deleteState(boolean delete) {
                if (delete) {
                    tv.setBackgroundResource(R.color.holo_red_dark);
                    tv.setText(getResources().getString(R.string.post_delete_tv_s));
                } else {
                    tv.setText(getResources().getString(R.string.post_delete_tv_d));
                    tv.setBackgroundResource(R.color.holo_red_light);
                }
            }

            @Override
            public void dragState(boolean start) {
                if (start) {
                    tv.setVisibility(View.VISIBLE);
                } else {
                    tv.setVisibility(View.GONE);
                }
            }
        });
    }

    //------------------图片相关-----------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {//从相册选择完图片
            //压缩图片
            new Thread(new MyRunnable(PostImagesActivity.this, data.getStringArrayListExtra(MultiImageSelectorActivity.EXTRA_RESULT),
                    originImages, dragImages, myHandler, true)).start();
        }
    }

    @OnClick(R.id.btn)
    public void onViewClicked() {
        mProgress.setVisibility(View.VISIBLE);
        String[] images = dragImages.toArray(new String[dragImages.size()]);
        for (int i = 0; i < originImages.size(); i++) {
            if (!originImages.get(i).contains(getString(R.string.glide_plus_icon_string))) {
                images[i] = originImages.get(i);
            }
        }
        BmobFile.uploadBatch(images, new UploadBatchListener() {
            @Override
            public void onSuccess(List<BmobFile> list, List<String> list1) {
                Log.d("jay", "onSuccess: [list, list1]=" + list1.size());
                if (list1.size() == dragImages.size()-1) {
                    ImageModule module = new ImageModule();
                    module.setUrls(list1);
                    module.update(getString(R.string.avatar_id), new UpdateListener() {
                        @Override
                        public void done(BmobException e) {
                            if (e == null) {
                                mProgress.setVisibility(View.GONE);
                            }
                        }
                    });

                    Toast.makeText(mContext, "success", Toast.LENGTH_SHORT).show();
                }

            }

            @Override
            public void onProgress(int i, int i1, int i2, int i3) {
                Log.d("jay", "onProgress: [i=" + i + ", i1=" + i1 + ", i2=" + i2 + ", i3]=" + i3);
            }

            @Override
            public void onError(int i, String s) {
                Log.d("jay", "onError: [i=" + i + ", s]=" + s);

            }
        });
    }

    /**
     * 另起线程压缩图片
     */
    static class MyRunnable implements Runnable {

        List<String> images;
        List<String> originImages;
        List<String> dragImages;
        Handler handler;
        boolean add;//是否为添加图片
        private final Context mContext;

        public MyRunnable(Context context, List<String> images, List<String> originImages, List<String> dragImages, Handler handler, boolean add) {
            this.images = images;
            this.originImages = originImages;
            this.dragImages = dragImages;
            this.handler = handler;
            this.add = add;
            mContext = context;
        }

        @Override
        public void run() {
            int addIndex = originImages.size() - 1;
            for (int i = 0; i < images.size(); i++) {
                if (images.get(i).contains(MyApplication.getInstance().getString(R.string.glide_plus_icon_string))) {//说明是添加图片按钮
                    continue;
                }
                Bitmap bitmap;
                try {
                    bitmap = PickImageHelper.getImageSampleOutput(mContext, Uri.fromFile(new File(images.get(i))));
                    Bitmap thumbNail = Bitmap.createScaledBitmap(bitmap, 200, 200, false);
                    String fileName = PickImageHelper.GenerateNameWithUUID();
                    File file = PickImageHelper.createFileFromBitmap(mContext, fileName, thumbNail);
                    if (!add) {
                        images.set(i, file.getPath());
                    } else {//添加图片，要更新
                        dragImages.add(addIndex, file.getPath());
                        originImages.add(addIndex++, file.getPath());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (OutOfMemoryError ome) {
                    ome.printStackTrace();
                }
            }
            Message message = new Message();
            message.what = 1;
            handler.sendMessage(message);
        }
    }

    private MyHandler myHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private WeakReference<Activity> reference;

        public MyHandler(Activity activity) {
            reference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            PostImagesActivity activity = (PostImagesActivity) reference.get();
            if (activity != null) {
                switch (msg.what) {
                    case 1:
                        activity.postArticleImgAdapter.notifyDataSetChanged();
                        break;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        myHandler.removeCallbacksAndMessages(null);
    }

}
