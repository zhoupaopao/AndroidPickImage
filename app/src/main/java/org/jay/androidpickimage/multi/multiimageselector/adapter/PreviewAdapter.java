package org.jay.androidpickimage.multi.multiimageselector.adapter;

import android.app.Activity;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.jay.androidpickimage.R;
import org.jay.androidpickimage.multi.multiimageselector.ImgSelConfig;
import org.jay.androidpickimage.multi.multiimageselector.bean.Image;
import org.jay.androidpickimage.multi.multiimageselector.common.Constant;
import org.jay.androidpickimage.multi.multiimageselector.common.OnItemClickListener;

import java.util.List;

/**
 * @author yuyh.
 * @date 2016/9/28.
 */
public class PreviewAdapter extends PagerAdapter {

    private Activity activity;
    private List<Image> images;
    private ImgSelConfig config;
    private OnItemClickListener listener;

    public PreviewAdapter(Activity activity, List<Image> images, ImgSelConfig config) {
        this.activity = activity;
        this.images = images;
        this.config = config;
    }

    @Override
    public int getCount() {
        if (config.needCamera)
            return images.size() - 1;
        else
            return images.size();
    }

    @Override
    public View instantiateItem(ViewGroup container, final int position) {
        View root = View.inflate(activity, R.layout.img_item_pager__sel, null);
        final ImageView photoView = (ImageView) root.findViewById(R.id.ivImage);
        final ImageView ivChecked = (ImageView) root.findViewById(R.id.ivPhotoCheaked);

        if (config.multiSelect) {

            ivChecked.setVisibility(View.VISIBLE);
            final Image image = images.get(config.needCamera ? position + 1 : position);
            if (Constant.imageList.contains(image.path)) {
                ivChecked.setImageResource(R.drawable.ic_img_checked);
            } else {
                ivChecked.setImageResource(R.drawable.ic_img_uncheck);
            }

            ivChecked.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        int ret = listener.onCheckedClick(position, image);
                        if (ret == 1) { // 局部刷新
                            if (Constant.imageList.contains(image.path)) {
                                ivChecked.setImageResource(R.drawable.ic_img_checked);
                            } else {
                                ivChecked.setImageResource(R.drawable.ic_img_uncheck);
                            }
                        }
                    }
                }
            });

            photoView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onImageClick(position, images.get(position));
                    }
                }
            });
        } else {
            ivChecked.setVisibility(View.GONE);
        }

        container.addView(root, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        displayImage(photoView, images.get(config.needCamera ? position + 1 : position).path);

        return root;
    }

    private void displayImage(ImageView photoView, String path) {
        config.loader.displayImage(activity, path, photoView,false);
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }

    public void setListener(OnItemClickListener listener) {
        this.listener = listener;
    }
}
