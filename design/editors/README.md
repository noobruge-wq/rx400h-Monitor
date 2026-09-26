# 历史浏览器UI编辑工具

- `pixel-v2/RX400h-UI-Pixel-Editor-v2.html`：像素版编辑器；同目录保留模板和辅助源脚本。
- `original/RX400h-UI-Layout-Editor.html`：更早的布局编辑器，仅作历史参考。
- 应导入仓库 `app/src/main/assets/rx400h_ui/layout_v035_pixel_v2.json` 作为当前用户定稿布局。历史HTML内置示例/辅助JSON不代表D064当前布局。
- 脚本按原字节保存，引用旧下载目录、图片、SDK或workspace时需要在新电脑上核对路径；没有在本轮重跑图像处理，也没有修改用户绘图。
- 浏览器自身的未导出localStorage不能靠Git保存；用户导出的最终布局已在正式assets中保留。额外未导出的编辑器操作需要用户在清空旧电脑前自行确认。
