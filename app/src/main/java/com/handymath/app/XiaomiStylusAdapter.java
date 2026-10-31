package com.handymath.app;

/** No proprietary auth is claimed: this channel uses standard Xiaomi/Redmi pen events. */
final class XiaomiStylusAdapter extends ManagedStylusAdapter {
  public String name(){return "小米 / Redmi 手写笔 · 标准压感 / 侧键 / 预测";}
  public boolean supports(String maker){return maker.contains("xiaomi")||maker.contains("redmi")||maker.contains("poco");}
}
