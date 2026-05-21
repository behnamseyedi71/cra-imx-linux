# Restrict libcamera to pipelines that build without platform-specific
# hardware libraries. The rpi/pisp pipeline requires libpisp (Raspberry Pi 5
# only) which is not available for i.MX8MP and cannot be auto-downloaded
# because Yocto disables meson wrap fetching at build time.
#
# Pipelines kept:
#   simple  — generic V4L2 pipeline, covers the i.MX8MP ISI/MIPI-CSI camera
#   uvcusb  — USB UVC cameras (useful for development)
LIBCAMERA_PIPELINES = "imx8-isi,simple,uvcvideo"
