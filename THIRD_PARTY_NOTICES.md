# Third-party notices

The EdgeOS image bundles a large amount of third-party software. Most of it is
under permissive licenses (MIT, BSD, Apache-2.0) and carries no obligation
beyond preserving the license texts in the image at `/usr/share/licenses/`,
which the build does automatically via `COPY_LIC_DIRS = "1"` in the distro
config.

This file lists the components whose licenses have real practical consequences
for anyone redistributing the image or a device that runs it.

## What the MIT LICENSE in this repo covers

The [LICENSE](LICENSE) file at the root of this repository applies only to the
files originally written for this project: the `meta-edgeos` Yocto layer, the
demo CLIs (`edgeos-detect`, `edgeos-benchmark`, `edgeos-perfctl`), the host
helper scripts under `bin/`, the build configuration in
`build-edgeos-headless/conf/`, and the documentation.

Everything else fetched by `repo sync` (poky, meta-imx, meta-openembedded,
meta-ros, etc.) keeps its own original license. The notices below cover the
items from those upstream layers that have practical redistribution
consequences.

## AGPL-3.0

| Package | Source | License |
|---------|--------|---------|
| `python3-ultralytics` 8.3.119 | https://github.com/ultralytics/ultralytics | AGPL-3.0-only |
| `yolov8n.onnx` (weights) | Ultralytics | AGPL-3.0-only (derived from the same project) |

The AGPL is a strong copyleft license and explicitly covers network use. If
you ship a product that runs Ultralytics YOLO, or expose its functionality
over a network, anyone receiving the binary or using that service is entitled
to the complete corresponding source code.

The Yocto `archiver` bbclass enabled in `edgeos.conf` captures the patched
source under `DEPLOY_DIR/sources/` at build time. If you publish the image,
publish that source archive alongside it.

If AGPL does not fit your use case, Ultralytics offers a separate commercial
license. The other option is to drop `python3-ultralytics` from the image
recipe and swap the demo for a permissively licensed detector (for example a
self-trained YOLOv5 export, or a model from a BSD/Apache project).

## GPL-2.0, GPL-3.0, LGPL

The Linux kernel (GPL-2.0), BusyBox (GPL-2.0), glibc (LGPL-2.1),
GStreamer (LGPL-2.1) and many smaller utilities are GPL or LGPL. The build
archives their corresponding source automatically into `DEPLOY_DIR/sources/`.
If you ship the wic image, you either need to host that archive on your
download page or fulfil the obligation with a written offer accompanying the
device.

## NXP Software License Agreement (EULA)

| Package | What it is |
|---------|------------|
| `firmware-ele-imx` | EdgeLock Enclave firmware |
| `imx-gpu-viv` | Vivante GPU userspace driver |
| `imx-gpu-g2d` | 2D GPU acceleration library |
| `imx-vpu-hantro` | VPU firmware and userspace components |
| `imx-codec` | NXP multimedia codec components |
| `firmware-nxp-wifi-nxpiw416-sdio` | IW416 WiFi/BT firmware |

These are proprietary binary blobs distributed under the NXP / Freescale
Semiconductor Software License Agreement. The line `ACCEPT_FSL_EULA = "1"` in
`build-edgeos-headless/conf/local.conf` is what lets the build pull them in.

If you redistribute the built `.wic` image publicly, the NXP EULA requires
that your download page present the EULA text and require the user to accept
it before the download begins. The full text lives at `sources/meta-imx/EULA`
after `repo sync` has run.

## Apache-2.0 / MIT / BSD components

These ship in the image and require no per-package action beyond keeping the
license texts on the device (handled automatically). Listed here for
completeness:

- TensorFlow / TensorFlow Lite (Apache-2.0)
- ONNX Runtime (MIT)
- OpenCV (Apache-2.0)
- ROS 2 Humble (mostly Apache-2.0)
- Yocto / Poky / OpenEmbedded (MIT)
- ConnMan (GPL-2.0, source archived as above)
- BlueZ (GPL-2.0 / LGPL-2.1)
- wpa-supplicant (BSD-3-Clause)

## Per-image license manifest

For an exhaustive per-package list of every license that ended up in a given
build, look inside the deploy directory after building:

```
build-edgeos-headless/tmp/deploy/images/imx8mp-lpddr4-evk/edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.manifest
build-edgeos-headless/tmp/deploy/licenses/edgeos-image-headless-imx8mp-lpddr4-evk/
```

The first is a short package -> license map. The second contains the full
license text of every component installed in that build.
