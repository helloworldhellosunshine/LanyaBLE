package com.xuexi.lanyable;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.TextView;

/**动画页面
 * Created by 王帝 on 2017/12/15.
 */

public class Ui_shanping extends AppCompatActivity{

    private TextView tv_shan;

    //组合动画
    private AnimationSet set;
    //时间
    private int animTime=2000;

    private Handler handler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 1001:
                    startActivity(new Intent(Ui_shanping.this,MainActivity.class));
                    finish();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.shanping);

        initView();
    }
    //初始化View
    private void initView() {
        tv_shan= (TextView) findViewById(R.id.shan);

        //是否共用一个动画
        set=new AnimationSet(true);
        set.setDuration(animTime);
        //动画执行完之后保持原有状态
        set.setFillAfter(true);

        //缩放
        ScaleAnimation scale=new ScaleAnimation(0,1,0,1);
        scale.setDuration(animTime);
        set.addAnimation(scale);

        //平移
        TranslateAnimation translate=new TranslateAnimation(0,0,0,-200);
        translate.setDuration(animTime);
        set.addAnimation(translate);

        //执行动画
        tv_shan.startAnimation(set);

        //执行动画监听
        set.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                handler.sendEmptyMessageDelayed(1001,1000);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }
    //锁死返回键
    @Override
    public void onBackPressed() {
        //super.onBackPressed();

    }
}
