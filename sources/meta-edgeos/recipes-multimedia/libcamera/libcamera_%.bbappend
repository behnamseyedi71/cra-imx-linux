# Restrict libcamera to pipelines that build without platform-specific
# hardware libraries. The rpi/pisp pipeline requires libpisp (Raspberry Pi 5
# only) which is not available for i.MX8MP and cannot be auto-downloaded
# because Yocto disables meson wrap fetching at build time.
#
# Pipelines kept:
#   imx8-isi  on-board ISI / MIPI-CSI camera for the i.MX8M Plus
#   simple    generic V4L2 pipeline
#   uvcvideo  USB UVC webcams (useful for development)
LIBCAMERA_PIPELINES = "imx8-isi,simple,uvcvideo"
