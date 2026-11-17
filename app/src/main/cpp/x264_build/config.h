/*
 * Hand-written equivalent of x264's configure output for Android arm64-v8a (NDK r27 clang).
 * Matches: ./configure --host=aarch64-linux-android --bit-depth=8 --disable-interlaced
 *          --disable-opencl --disable-cli --enable-static (no lavf/ffms/swscale/lsmash/gpac).
 * SVE/SVE2 asm is left out (rare on phones); NEON + dotprod/i8mm are runtime-detected via HWCAP.
 */
#define HAVE_MALLOC_H 1
#define HAVE_ALTIVEC 0
#define HAVE_ALTIVEC_H 0
#define HAVE_MMX 0
#define HAVE_ARMV6 0
#define HAVE_ARMV6T2 0
#define HAVE_NEON 1
#define HAVE_AARCH64 1
#define HAVE_BEOSTHREAD 0
#define HAVE_POSIXTHREAD 1
#define HAVE_WIN32THREAD 0
#define HAVE_THREAD 1
#define HAVE_LOG2F 1
#define HAVE_SWSCALE 0
#define HAVE_LAVF 0
#define HAVE_FFMS 0
#define HAVE_GPAC 0
#define HAVE_AVS 0
#define HAVE_GPL 1
#define HAVE_VECTOREXT 0
#define HAVE_INTERLACED 0
#define HAVE_CPU_COUNT 1
#define HAVE_OPENCL 0
#define HAVE_THP 0
#define HAVE_LSMASH 0
#define HAVE_X86_INLINE_ASM 0
#define HAVE_AS_FUNC 0
#define HAVE_INTEL_DISPATCHER 0
#define HAVE_MSA 0
#define HAVE_LSX 0
#define HAVE_MMAP 1
#define HAVE_WINRT 0
#define HAVE_VSX 0
#define HAVE_ARM_INLINE_ASM 0
#define HAVE_STRTOK_R 1
#define HAVE_CLOCK_GETTIME 1
#define HAVE_BITDEPTH8 1
#define HAVE_BITDEPTH10 0
#define HAVE_ELF_AUX_INFO 0
#define HAVE_GETAUXVAL 1
#define HAVE_SYSCONF 1
#define HAVE_SYNC_FETCH_AND_ADD 1
#define HAVE_DOTPROD 1
#define HAVE_I8MM 1
#define HAVE_SVE 0
#define HAVE_SVE2 0
#define HAVE_RISCV64 0
#define HAVE_RVV 0
#define HAVE_AS_ARCHEXT_DOTPROD_DIRECTIVE 1
#define HAVE_AS_ARCHEXT_I8MM_DIRECTIVE 1
#define HAVE_AS_ARCHEXT_SVE_DIRECTIVE 0
#define HAVE_AS_ARCHEXT_SVE2_DIRECTIVE 0
#define ARCH_AARCH64 1
#define SYS_LINUX 1
#define STACK_ALIGNMENT 16
#define AS_ARCH_LEVEL armv8.6-a+crc
