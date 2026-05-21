# EdgeOS — i.MX8MP Build & Flash Guide

EdgeOS is a headless Yocto Linux image for the NXP i.MX8MP (imx8mp-lpddr4-evk) development board.
It includes a full ML inference stack (TFLite + ONNX Runtime + YOLOv8), ROS2 Humble, GStreamer with
NXP hardware codecs, and CRA-compliance tooling (CVE scan, SPDX SBOM, GPL archiver).

---

## 1. Host Prerequisites

Tested on **Ubuntu 22.04 / 24.04 LTS**, x86-64, with at least **40 GB RAM** and **500 GB** free disk.

```bash
sudo apt update && sudo apt install -y \
    gawk wget git diffstat unzip texinfo gcc build-essential \
    chrpath socat cpio python3 python3-pip python3-pexpect \
    xz-utils debianutils iputils-ping python3-git python3-jinja2 \
    libegl1-mesa libsdl1.2-dev pylint xterm python3-subunit \
    mesa-common-dev zstd liblz4-tool file locales libacl1 \
    repo curl

# Install bmaptool for fast SD card flashing
sudo apt install -y bmap-tools
```

---

## 2. Get the Sources

```bash
mkdir cra-imx-linux && cd cra-imx-linux

# Clone this repo
git clone https://github.com/behnamseyedi71/cra-imx-linux.git .

# Init the NXP BSP repo manifest (uses our modified manifest with meta-ros added)
repo init -u https://source.codeaurora.org/external/imx/imx-manifest \
          -b imx-linux-scarthgap \
          -m imx-6.6.52-2.2.2.xml

# Overlay our modified manifest (adds meta-ros remote + project)
cp manifests/imx-6.6.52-2.2.2.xml .repo/manifests/imx-6.6.52-2.2.2.xml

# Sync all upstream layers (~15 GB, takes 20–40 min depending on connection)
repo sync -j8
```

> **NXP EULA:** The NXP BSP includes proprietary GPU and NPU firmware blobs. By building this image
> you accept the NXP/Freescale EULA. `ACCEPT_FSL_EULA = "1"` is already set in `local.conf`.

---

## 3. Set Up the Build Environment

```bash
# Source the NXP setup script — creates build-edgeos-headless/ on first run
source setup-environment build-edgeos-headless

# The script drops you into build-edgeos-headless/. Go back to the root.
cd ..
```

The `build-edgeos-headless/conf/` files in this repo replace the auto-generated ones. They are
already in place after the `git clone` above — no manual editing needed.

---

## 4. Build the Image

```bash
cd build-edgeos-headless
bitbake edgeos-image-headless
```

**Build time:** 6–12 hours on a fresh machine (16 cores, 40 GB RAM).  
**Incremental builds** (sstate cache warm): 15–30 minutes.

### Memory-intensive recipes

| Recipe | Peak RAM | Note |
|--------|----------|------|
| onnxruntime | 8–12 GB | LLVM-style optimisation passes |
| tensorflow-lite | 4–6 GB | XNNPack / RUY link |
| opencv | 2–3 GB | contrib module template instantiation |

If the build OOM-crashes, run once as root before starting:
```bash
echo 1 | sudo tee /proc/sys/vm/overcommit_memory
sudo sysctl -w vm.swappiness=10
```

### Output images

After a successful build, images land in:
```
build-edgeos-headless/tmp/deploy/images/imx8mp-lpddr4-evk/
```

| File | Purpose |
|------|---------|
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.zst` | SD card image (write this) |
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.bmap` | Bmap index (speeds up flash) |
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.manifest` | Full package list |
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.spdx.tar.zst` | SPDX SBOM (CRA compliance) |
| `edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.cve` | CVE scan report |

---

## 5. Flash to SD Card

```bash
cd build-edgeos-headless/tmp/deploy/images/imx8mp-lpddr4-evk/

# Identify your SD card (e.g. /dev/sdb or /dev/mmcblk0) — double-check before writing!
lsblk

# Flash using bmaptool (fast, verifies blocks)
sudo bmaptool copy \
    edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.zst \
    /dev/sdX          # <-- replace sdX with your device

# Alternative: raw dd (slower, no verification)
# zstd -d edgeos-image-headless-imx8mp-lpddr4-evk.rootfs.wic.zst -o rootfs.wic
# sudo dd if=rootfs.wic of=/dev/sdX bs=4M conv=fsync status=progress
```

> **Warning:** Writing to the wrong device will destroy data. Verify with `lsblk` first.

---

## 6. First Boot

1. Insert the SD card into the i.MX8MP board's SD slot.
2. Set boot switches to SD card boot mode (see NXP EVK Quick Start Guide).
3. Connect UART debug console: `115200 8N1`, no flow control.
4. Power on. U-Boot will load from SD, then Linux boots.

**Default credentials:** `root` / `edgeos2026`

**SSH access** (after network is up):
```bash
ssh root@<board-ip>
```

### Verify ML stack on the board

```bash
# TFLite
python3 -c "import tflite_runtime.interpreter as tflite; print('TFLite OK')"

# ONNX Runtime
python3 -c "import onnxruntime; print('ORT', onnxruntime.__version__)"

# YOLOv8
python3 -c "from ultralytics import YOLO; print('Ultralytics OK')"

# ROS2
source /opt/ros/humble/setup.sh
ros2 --help
```

---

## 7. Package Management on the Device

EdgeOS uses **opkg** (not apt/deb):

```bash
opkg update
opkg list-installed
opkg install <package-name>
```

The package feed is not pre-configured — packages come from the image only unless you set up a
local package server from `tmp/deploy/ipk/`.

---

## 8. CRA Compliance Artefacts

The build automatically produces:

| Artefact | Location | Purpose |
|----------|----------|---------|
| SPDX SBOM | `tmp/deploy/images/…/rootfs.spdx.tar.zst` | Bill of Materials (CRA Art. 13) |
| CVE report | `tmp/deploy/images/…/rootfs.cve` | Known vulnerability scan |
| GPL sources | `tmp/deploy/sources/` | Source code archive (GPL/LGPL/AGPL) |
| License manifest | `tmp/deploy/images/…/rootfs.manifest` | Per-package licence list |

> **AGPL-3.0 notice:** `python3-ultralytics` (YOLOv8) is licensed under AGPL-3.0. If you distribute
> a device running this image you must make the complete source code available. The GPL archiver
> in this build captures it automatically in `tmp/deploy/sources/`.

---

## 9. Next Development Steps

### Add a custom application
Create a new recipe under `sources/meta-edgeos/recipes-apps/<appname>/` and add it to
`IMAGE_INSTALL:append` in `recipes-core/images/edgeos-image-headless.bb`.

### Run YOLOv8 inference
```python
from ultralytics import YOLO
model = YOLO("yolov8n.onnx")          # export from desktop first
results = model("/path/to/image.jpg")
```
Export a model to ONNX on a desktop machine:
```bash
pip install ultralytics
yolo export model=yolov8n.pt format=onnx imgsz=640
scp yolov8n.onnx root@<board-ip>:/home/root/
```

### Enable NXP NPU delegate for TFLite
The VX delegate (`tensorflow-lite-vx-delegate`) is already installed. Use it in Python:
```python
import tflite_runtime.interpreter as tflite
delegate = tflite.load_delegate('libvx_delegate.so')
interpreter = tflite.Interpreter(model_path="model.tflite", experimental_delegates=[delegate])
```

### Update to a newer NXP BSP
1. Change the manifest file in `manifests/` to the new BSP tag.
2. Run `repo sync`.
3. Update `PV` and checksums in `sources/meta-edgeos/recipes-ai/yolo/python3-ultralytics_8.3.bb`
   if a newer ultralytics version is needed.
4. Rebuild.

### Reduce image size
Remove packages from `IMAGE_INSTALL` in the image recipe that are not needed for your application.
ROS2 (`ros-base`) is the largest single addition (~150 MB compressed). If not needed, remove it.

---

## 10. Repo Structure

```
cra-imx-linux/
├── manifests/
│   └── imx-6.6.52-2.2.2.xml        # NXP BSP manifest + meta-ros addition
├── sources/
│   └── meta-edgeos/                 # Custom Yocto layer (only our code)
│       ├── conf/
│       │   ├── layer.conf
│       │   └── distro/edgeos.conf
│       ├── recipes-ai/yolo/
│       │   └── python3-ultralytics_8.3.bb
│       ├── recipes-core/images/
│       │   └── edgeos-image-headless.bb
│       ├── recipes-multimedia/libcamera/
│       │   └── libcamera_%.bbappend
│       └── recipes-nnstreamer/nnstreamer/
│           └── nnstreamer_%.bbappend
├── build-edgeos-headless/conf/
│   ├── local.conf                   # Build environment tuning (threads, RAM caps)
│   └── bblayers.conf                # Layer stack
├── INSTRUCTIONS.md                  # This file
└── .gitignore
```

All other directories (`sources/poky/`, `sources/meta-imx/`, etc.) are upstream layers fetched
by `repo sync` and are not tracked in this repository.
