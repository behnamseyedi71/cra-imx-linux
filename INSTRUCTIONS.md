# EdgeOS build and flash guide

EdgeOS is a headless Yocto Linux image for the NXP i.MX8M Plus EVK
(`imx8mp-lpddr4-evk`). The image carries a full ML inference stack
(TensorFlow Lite plus the NXP VX delegate, ONNX Runtime, YOLOv8), ROS 2
Humble, GStreamer with the NXP hardware codecs, and the CRA-compliance
tooling (CVE scan, SPDX SBOM, GPL/AGPL source archiver).

This document walks through the full process: host setup, sources, build,
flash and first boot. If something blows up during the build, look in
[BUILD_TROUBLESHOOTING.md](BUILD_TROUBLESHOOTING.md) first. Every error I
hit on a clean Ubuntu 24.04 host is documented there with the exact fix.

---

## 1. Host prerequisites

I built this on **Ubuntu 22.04 / 24.04 LTS**, x86-64. Other distros will
probably work but I have not tested them.

| Resource | Recommended | Minimum |
|----------|-------------|---------|
| RAM      | 40 GB       | 32 GB plus a 32 GB swapfile (`bin/edgeos-host-setup` creates one) |
| Disk     | 500 GB      | 350 GB |
| Cores    | 16          | 6 (build will just take longer) |

The `local.conf` in this repo is tuned for a 32 GB host. On a 40+ GB machine
you can raise `BB_NUMBER_THREADS` to 8 and `PARALLEL_MAKE` to `-j 4` for
roughly 25-30% faster builds.

Install the build prerequisites:

```bash
sudo apt update && sudo apt install -y \
    gawk wget git diffstat unzip texinfo gcc build-essential \
    chrpath socat cpio python3 python3-pip python3-pexpect \
    xz-utils debianutils iputils-ping python3-git python3-jinja2 \
    libegl1-mesa libsdl1.2-dev pylint xterm python3-subunit \
    mesa-common-dev zstd liblz4-tool file locales libacl1 \
    repo curl

# bmaptool makes flashing the SD card a lot faster
sudo apt install -y bmap-tools
```

---

## 2. Get the sources

```bash
mkdir cra-imx-linux && cd cra-imx-linux

# 1. Clone this repo
git clone https://github.com/behnamseyedi71/cra-imx-linux.git .

# 2. Init the NXP BSP manifest
repo init -u https://source.codeaurora.org/external/imx/imx-manifest \
          -b imx-linux-scarthgap \
          -m imx-6.6.52-2.2.2.xml

# 3. Overlay the modified manifest (adds the meta-ros remote and project)
cp manifests/imx-6.6.52-2.2.2.xml .repo/manifests/imx-6.6.52-2.2.2.xml

# 4. Sync all upstream layers (~15 GB, takes 20-40 min on a decent connection)
repo sync -j8
```

A note on the NXP EULA: the BSP includes proprietary GPU and NPU firmware
blobs. By building this image you are accepting the NXP / Freescale Software
License Agreement. `ACCEPT_FSL_EULA = "1"` is already set in `local.conf`.

---

## 3. Set up the build environment

```bash
# This sources the NXP setup script and drops you into build-edgeos-headless/
source setup-environment build-edgeos-headless

# Go back to the workspace root for the rest of the steps
cd ..
```

The `build-edgeos-headless/conf/` files in this repo replace the
auto-generated ones, so you do not need to edit anything by hand. They are
already in place after the `git clone` in step 2.

---

## 4. Build the image

```bash
# One-time host tuning. Creates a 32 GB swapfile, sets vm.overcommit_memory=1
# and lowers vm.swappiness. Safe to run on 40+ GB hosts too; the swap just
# gives you extra headroom.
sudo bin/edgeos-host-setup

cd build-edgeos-headless
bitbake edgeos-image-headless
```

Build time on a fresh machine is 6 to 12 hours on a 16-core / 40 GB host,
or 10 to 18 hours on a 32 GB / 8-core laptop with the swapfile.
Incremental builds against a warm sstate cache run in 15 to 30 minutes.

### Memory-intensive recipes

| Recipe | Peak RAM | Why |
|--------|----------|-----|
| onnxruntime | 8-12 GB | LLVM-style optimisation passes |
| tensorflow-lite | 4-6 GB | XNNPack / RUY link step |
| opencv | 2-3 GB | contrib module template instantiation |

If the build still gets OOM-killed after running `edgeos-host-setup`, drop
`BB_NUMBER_THREADS` to `4` and `PARALLEL_MAKE` to `-j 2` in
`build-edgeos-headless/conf/local.conf`.

### Output images

After a successful build, the artefacts land in:

```
build-edgeos-headless/tmp/deploy/images/imx8mp-lpddr4-evk/
```

| File | Purpose |
|------|---------|
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.zst` | SD card image (this is the one you flash) |
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.bmap` | Bmap index, speeds up the flash |
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.manifest` | Full package list |
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.spdx.tar.zst` | SPDX SBOM (CRA compliance) |
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.cve` | CVE scan report |

---

## 5. Flash to SD card

```bash
cd build-edgeos-headless/tmp/deploy/images/imx8mp-lpddr4-evk/

# Identify your SD card. Check it twice before writing.
lsblk

# Flash with bmaptool (fast and verifies blocks as it goes)
sudo bmaptool copy \
    edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.zst \
    /dev/sdX          # replace sdX with your device

# Or the manual route with dd:
# zstd -d edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.zst -o rootfs.wic
# sudo dd if=rootfs.wic of=/dev/sdX bs=4M conv=fsync status=progress
```

Writing to the wrong device will destroy data on it. Confirm with `lsblk`
before you run the command.

---

## 6. First boot

1. Insert the SD card into the i.MX8M Plus board's SD slot.
2. Set the boot switches to SD card boot mode (the NXP EVK Quick Start
   guide shows the dip-switch positions).
3. Connect a USB-to-serial cable for the debug console at `115200 8N1`,
   no flow control.
4. Power on. U-Boot will load from SD, then Linux boots.

Default credentials are `root` / `edgeos2026`. Change the password before
the device sees a real network. SSH access works as soon as Ethernet is up:

```bash
ssh root@<board-ip>
```

### Verify the ML stack on the board

```bash
# TFLite
python3 -c "import tflite_runtime.interpreter as tflite; print('TFLite OK')"

# ONNX Runtime, and check the NPU execution provider is registered
python3 -c "import onnxruntime as o; print('ORT', o.__version__); print(o.get_available_providers())"

# YOLOv8 demo. The model is pre-installed at /var/lib/edgeos-demo/yolov8n.onnx,
# runs on the NPU, no internet needed.
edgeos-detect --list-cameras
edgeos-detect

# ROS 2
source /opt/ros/humble/setup.sh
ros2 --help
```

The YOLO demo uses onnxruntime directly. It does *not* import the
ultralytics Python package on the device, so PyTorch is never pulled in.
The script picks `VsiNpuExecutionProvider` automatically and falls back to
CPU if the NPU provider is not present in the installed onnxruntime build.

---

## 7. Package management on the device

EdgeOS uses **opkg**, not apt or dpkg:

```bash
opkg update
opkg list-installed
opkg install <package-name>
```

There is also an `apt` / `apt-get` shim installed at `/usr/bin/apt` and
`/usr/bin/apt-get`. It maps the familiar Debian command line onto opkg so
you can `apt update`, `apt install` etc. without thinking about it.

The opkg package feed is not preconfigured. Packages come from the image
itself unless you set up a local feed server from `tmp/deploy/ipk/`.

---

## 8. CRA compliance artefacts

The build produces all of these automatically:

| Artefact | Location | What it is |
|----------|----------|-----------|
| SPDX SBOM | `tmp/deploy/images/.../rootfs.spdx.tar.zst` | Bill of materials (CRA Art. 13) |
| CVE report | `tmp/deploy/images/.../rootfs.cve` | Known vulnerability scan |
| GPL/AGPL source | `tmp/deploy/sources/` | Patched source archive for copyleft packages |
| License manifest | `tmp/deploy/images/.../rootfs.manifest` | Per-package licence list |

About the AGPL-3.0 notice for `python3-ultralytics` (YOLOv8): if you ship a
device that runs this image, the complete corresponding source has to be
available to recipients. The GPL archiver above captures it automatically
under `tmp/deploy/sources/`. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
for the rest of the licensing detail.

---

## 9. Next development steps

### Add a custom application

Create a new recipe under `sources/meta-edgeos/recipes-apps/<appname>/` and
add it to `IMAGE_INSTALL:append` in
`recipes-core/images/edgeos-image-headless.bb`.

### Run YOLOv8 inference from your own code

The headless image ships a pre-installed YOLOv8n ONNX model at
`/var/lib/edgeos-demo/yolov8n.onnx`. The bundled `edgeos-detect` tool uses
it directly via onnxruntime with the NPU provider, no Python ultralytics or
torch imports involved.

If you want to call the model from your own Python code:

```python
import onnxruntime as ort, numpy as np, cv2

sess = ort.InferenceSession(
    "/var/lib/edgeos-demo/yolov8n.onnx",
    providers=["VsiNpuExecutionProvider", "CPUExecutionProvider"],
)
# preprocess: letterbox to 640x640, RGB, /255, NCHW float32
# output shape: (1, 84, 8400). First 4 are cx,cy,w,h; remaining 80 are class scores.
```

To swap in a different `yolov8n.onnx` (for example a custom-trained model),
just overwrite the file on the board:

```bash
scp my_yolov8n.onnx root@<board-ip>:/var/lib/edgeos-demo/yolov8n.onnx
```

To regenerate the stock model from `.pt` on a desktop machine:

```bash
pip install ultralytics
yolo export model=yolov8n.pt format=onnx imgsz=640 opset=12 simplify=True
# Output: yolov8n.onnx (~12 MB, opset 12, shape 1x3x640x640 -> 1x84x8400)
```

### Use the NXP NPU VX delegate for TFLite

The VX delegate (`tensorflow-lite-vx-delegate`) is already installed. Use it
from Python like this:

```python
import tflite_runtime.interpreter as tflite

delegate = tflite.load_delegate('libvx_delegate.so')
interpreter = tflite.Interpreter(
    model_path="model.tflite",
    experimental_delegates=[delegate],
)
```

### Update to a newer NXP BSP

1. Change the manifest file in `manifests/` to the new BSP tag.
2. Run `repo sync`.
3. Bump `PV` and the checksums in
   `sources/meta-edgeos/recipes-ai/yolo/python3-ultralytics_8.3.bb` if you
   also want a newer ultralytics.
4. Rebuild.

### Reduce image size

Remove packages from `IMAGE_INSTALL` in the image recipe that you do not
need. ROS 2 (`ros-base`) is by far the largest single addition at roughly
150 MB compressed. If you are not building a ROS node, drop it.

---

## 10. Repo layout

```
cra-imx-linux/
  manifests/
    imx-6.6.52-2.2.2.xml             NXP BSP manifest with meta-ros added
  sources/
    meta-edgeos/                      Custom Yocto layer (the only code in this repo)
      conf/
        layer.conf
        distro/edgeos.conf
      recipes-ai/yolo/
        python3-ultralytics_8.3.bb
      recipes-core/
        images/edgeos-image-headless.bb
        apt-compat/                   apt -> opkg shim
      recipes-demo/edgeos-demo/       edgeos-detect, edgeos-benchmark
      recipes-tools/edgeos-perfctl/   Performance governor CLI
      recipes-multimedia/libcamera/
      recipes-nnstreamer/nnstreamer/
  build-edgeos-headless/conf/
    local.conf                        Build environment tuning (threads, RAM caps)
    bblayers.conf                     Layer stack
  bin/                                edgeos-host-setup, edgeos-run-build, repo
  INSTRUCTIONS.md                     This file
  BUILD_TROUBLESHOOTING.md
  THIRD_PARTY_NOTICES.md
  LICENSE
  .gitignore
```

All other directories under `sources/` (`poky/`, `meta-imx/`,
`meta-openembedded/`, `meta-ros/`, etc.) are upstream layers fetched by
`repo sync` and are not tracked in this repository.
