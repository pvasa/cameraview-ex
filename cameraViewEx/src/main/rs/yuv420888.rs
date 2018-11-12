#pragma version(1)
#pragma rs java_package_name(com.priyankvasa.android.cameraviewex);
#pragma rs_fp_relaxed

int32_t width;
int32_t height;

uint picWidth, uvPixelStride, uvRowStride;
rs_allocation ypsIn, uIn, vIn;

// The LaunchOptions ensure that the Kernel does not enter the padding zone of Y,
// so yRowStride can be ignored WITHIN the Kernel.
uchar4 __attribute__((kernel)) doConvert(uint32_t x, uint32_t y) {

    // index for accessing the uIn's and vIn's
    uint uvIndex = (uvPixelStride * (x / 2)) + (uvRowStride * (y / 2));

    // get the y, u, v values
    uchar yps = rsGetElementAt_uchar(ypsIn, x, y);
    uchar u = rsGetElementAt_uchar(uIn, uvIndex);
    uchar v = rsGetElementAt_uchar(vIn, uvIndex);

    // calc argb
    int4 argb;
        argb.r = yps + ((v * 1436) / 1024) - 179;
        argb.g = yps - ((u * 46549) / 131072) + 44 - ((v * 93604) / 131072) + 91;
        argb.b = yps + ((u * 1814) / 1024) - 227;
        argb.a = 255;

    uchar4 out = convert_uchar4(clamp(argb, 0, 255));
    return out;
}