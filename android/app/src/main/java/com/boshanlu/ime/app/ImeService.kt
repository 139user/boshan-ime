package com.boshanlu.ime.app

import com.boshanlu.ime.BoshanIme

/** 独立 App 的输入法服务:键盘全在 ime 库的 BoshanIme 里,这里只告诉它去哪儿取管理员定的默认设置 */
class ImeService : BoshanIme() {
  override val configUrl: String get() = CONFIG_URL

  companion object {
    /** 博山后台(server/,部署在 VPS 上) */
    const val CONFIG_URL = "https://boshan.zhilin.cowork.nj7.uk/api/config"
  }
}
