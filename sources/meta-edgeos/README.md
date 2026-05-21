# meta-edgeos

Yocto layer for the EdgeOS headless robotics and edge-AI image targeting the
NXP i.MX8MP (imx8mp-lpddr4-evk). Built on top of the NXP Scarthgap BSP
release `imx-6.6.52-2.2.2`.

## Layer contents

| Path | Purpose |
|------|---------|
| `conf/distro/edgeos.conf` | Custom distro — extends `fsl-imx-fb`, sets identity, CRA compliance |
| `recipes-core/images/edgeos-image-headless.bb` | Production image recipe |
| `recipes-ai/yolo/python3-ultralytics_8.3.bb` | YOLOv8 inference (ONNX Runtime backend) |

## Building

```bash
# First-time setup
source imx-setup-release.sh -b build-edgeos-headless

# Build the image
bitbake edgeos-image-headless
```

## Licensing note

`python3-ultralytics` is AGPL-3.0. Review implications for your product
distribution before shipping a commercial image.
