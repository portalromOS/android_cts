/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.cts;

import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.display.DisplayManager;
import android.icu.util.ULocale;
import android.media.AudioFormat;
import android.media.AudioPresentation;
import android.media.DrmInitData;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import static android.media.MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.Display;
import android.view.Display.HdrCapabilities;
import android.webkit.cts.CtsTestServer;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

@AppModeFull(reason = "Instant apps cannot access the SD card")
public class MediaExtractorTest extends AndroidTestCase {
    private static final String TAG = "MediaExtractorTest";
    private static final UUID UUID_WIDEVINE = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    private static final UUID UUID_PLAYREADY = new UUID(0x9A04F07998404286L, 0xAB92E65BE0885F95L);
    private static boolean mIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    private static final boolean IS_AT_LEAST_S = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    static final String mInpPrefix = WorkDir.getMediaDirString();
    protected MediaExtractor mExtractor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mExtractor = new MediaExtractor();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mExtractor.release();
    }

    protected AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        File inpFile = new File(mInpPrefix + res);
        Preconditions.assertTestFileExists(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    protected TestMediaDataSource getDataSourceFor(final String res) throws Exception {
        AssetFileDescriptor afd = getAssetFileDescriptorFor(res);
        return TestMediaDataSource.fromAssetFd(afd);
    }

    protected TestMediaDataSource setDataSource(final String res) throws Exception {
        TestMediaDataSource ds = getDataSourceFor(res);
        mExtractor.setDataSource(ds);
        return ds;
    }

    public void SKIP_testNullMediaDataSourceIsRejected() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorUnitTest$TestApi
        // #testIfNullMediaDataSourceIsRejectedBySetDataSource
        try {
            mExtractor.setDataSource((MediaDataSource)null);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException ex) {
            // Expected, test passed.
        }
    }

    public void SKIP_testMediaDataSourceIsClosedOnRelease() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$SetDataSourceTest#testMediaDataSource
        TestMediaDataSource dataSource = setDataSource("testvideo.3gp");
        mExtractor.release();
        assertTrue(dataSource.isClosed());
    }

    public void SKIP_testExtractorFailsIfMediaDataSourceThrows() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorUnitTest$TestApi
        // #testIfInvalidDataSourceIsRejectedBySetDataSource
        TestMediaDataSource dataSource = getDataSourceFor("testvideo.3gp");
        dataSource.throwFromReadAt();
        try {
            mExtractor.setDataSource(dataSource);
            fail("Expected IOException.");
        } catch (IOException e) {
            // Expected.
        }
    }

    public void testExtractorFailsIfMediaDataSourceReturnsAnError() throws Exception {
        TestMediaDataSource dataSource = getDataSourceFor("testvideo.3gp");
        dataSource.returnFromReadAt(-2);
        try {
            mExtractor.setDataSource(dataSource);
            fail("Expected IOException.");
        } catch (IOException e) {
            // Expected.
        }
    }

    // Smoke test MediaExtractor reading from a DataSource.
    public void SKIP_testExtractFromAMediaDataSource() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$SetDataSourceTest#testMediaDataSource and
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest#testMetrics
        TestMediaDataSource dataSource = setDataSource("testvideo.3gp");
        checkExtractorSamplesAndMetrics();
    }

    // Smoke test MediaExtractor reading from an AssetFileDescriptor.
    public void SKIP_testExtractFromAssetFileDescriptor() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$SetDataSourceTest#testAssetFD and
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest#testMetrics
       AssetFileDescriptor afd = getAssetFileDescriptorFor("testvideo.3gp");
        mExtractor.setDataSource(afd);
        checkExtractorSamplesAndMetrics();
        afd.close();
    }

    private boolean advertisesDolbyVision() {
        // Device advertises support for DV if 1) it has a DV decoder, OR
        // 2) it lists DV on the Display HDR capabilities.
        if (MediaUtils.hasDecoder(MIMETYPE_VIDEO_DOLBY_VISION)) {
            return true;
        }

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        HdrCapabilities cap = defaultDisplay.getHdrCapabilities();
        for (int type : cap.getSupportedHdrTypes()) {
            if (type == HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
                return true;
            }
        }
        return false;
    }

    // DolbyVisionMediaExtractor for profile-level (DvheDtr/Fhd30).
    @CddTest(requirement="5.3.8")
    public void testDolbyVisionMediaExtractorProfileDvheDtr() throws Exception {
        TestMediaDataSource dataSource = setDataSource("video_dovi_1920x1080_30fps_dvhe_04.mp4");

        assertTrue("There should be either 1 or 2 tracks",
            0 < mExtractor.getTrackCount() && 3 > mExtractor.getTrackCount());

        MediaFormat trackFormat = mExtractor.getTrackFormat(0);
        int trackCountForDolbyVision = 1;

        // Handle the case where there is a Dolby Vision extractor
        // Note that it may or may not have a Dolby Vision Decoder
        if (mExtractor.getTrackCount() == 2) {
            if (trackFormat.getString(MediaFormat.KEY_MIME)
                    .equalsIgnoreCase(MIMETYPE_VIDEO_DOLBY_VISION)) {
                trackFormat = mExtractor.getTrackFormat(1);
                trackCountForDolbyVision = 0;
            }
        }

        if (advertisesDolbyVision()) {
            assertEquals("There must be 2 tracks", 2, mExtractor.getTrackCount());

            MediaFormat trackFormatForDolbyVision =
                mExtractor.getTrackFormat(trackCountForDolbyVision);

            final String mimeType = trackFormatForDolbyVision.getString(MediaFormat.KEY_MIME);
            assertEquals("video/dolby-vision", mimeType);

            int profile = trackFormatForDolbyVision.getInteger(MediaFormat.KEY_PROFILE);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr, profile);

            int level = trackFormatForDolbyVision.getInteger(MediaFormat.KEY_LEVEL);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd30, level);

            final int trackIdForDolbyVision =
                trackFormatForDolbyVision.getInteger(MediaFormat.KEY_TRACK_ID);

            final int trackIdForBackwardCompat = trackFormat.getInteger(MediaFormat.KEY_TRACK_ID);
            assertEquals(trackIdForDolbyVision, trackIdForBackwardCompat);
        }

        // The backward-compatible track should have mime video/hevc
        final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);
        assertEquals("video/hevc", mimeType);
    }

    // DolbyVisionMediaExtractor for profile-level (DvheStn/Fhd60).
    @CddTest(requirement="5.3.8")
    public void SKIP_testDolbyVisionMediaExtractorProfileDvheStn() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$ValidateKeyValuePairs[video/dolby-vision]
        TestMediaDataSource dataSource = setDataSource("video_dovi_1920x1080_60fps_dvhe_05.mp4");

        if (advertisesDolbyVision()) {
            // DvheStn exposes only a single non-backward compatible Dolby Vision HDR track.
            assertEquals("There must be 1 track", 1, mExtractor.getTrackCount());
            final MediaFormat trackFormat = mExtractor.getTrackFormat(0);

            final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);
            assertEquals("video/dolby-vision", mimeType);

            final int profile = trackFormat.getInteger(MediaFormat.KEY_PROFILE);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn, profile);

            final int level = trackFormat.getInteger(MediaFormat.KEY_LEVEL);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60, level);
        } else {
            MediaUtils.skipTest("Device does not provide a Dolby Vision decoder");
        }
    }

    // DolbyVisionMediaExtractor for profile-level (DvheSt/Fhd60).
    @CddTest(requirement="5.3.8")
    public void testDolbyVisionMediaExtractorProfileDvheSt() throws Exception {
        TestMediaDataSource dataSource = setDataSource("video_dovi_1920x1080_60fps_dvhe_08.mp4");

        assertTrue("There should be either 1 or 2 tracks",
            0 < mExtractor.getTrackCount() && 3 > mExtractor.getTrackCount());

        MediaFormat trackFormat = mExtractor.getTrackFormat(0);
        int trackCountForDolbyVision = 1;

        // Handle the case where there is a Dolby Vision extractor
        // Note that it may or may not have a Dolby Vision Decoder
        if (mExtractor.getTrackCount() == 2) {
            if (trackFormat.getString(MediaFormat.KEY_MIME)
                    .equalsIgnoreCase(MIMETYPE_VIDEO_DOLBY_VISION)) {
                trackFormat = mExtractor.getTrackFormat(1);
                trackCountForDolbyVision = 0;
            }
        }

        if (advertisesDolbyVision()) {
            assertEquals("There must be 2 tracks", 2, mExtractor.getTrackCount());

            MediaFormat trackFormatForDolbyVision =
                mExtractor.getTrackFormat(trackCountForDolbyVision);

            final String mimeType = trackFormatForDolbyVision.getString(MediaFormat.KEY_MIME);
            assertEquals("video/dolby-vision", mimeType);

            int profile = trackFormatForDolbyVision.getInteger(MediaFormat.KEY_PROFILE);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt, profile);

            int level = trackFormatForDolbyVision.getInteger(MediaFormat.KEY_LEVEL);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60, level);

            final int trackIdForDolbyVision =
                trackFormatForDolbyVision.getInteger(MediaFormat.KEY_TRACK_ID);

            final int trackIdForBackwardCompat = trackFormat.getInteger(MediaFormat.KEY_TRACK_ID);
            assertEquals(trackIdForDolbyVision, trackIdForBackwardCompat);
        }

        // The backward-compatible track should have mime video/hevc
        final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);
        assertEquals("video/hevc", mimeType);
    }

    // DolbyVisionMediaExtractor for profile-level (DvavSe/Fhd60).
    @CddTest(requirement="5.3.8")
    public void testDolbyVisionMediaExtractorProfileDvavSe() throws Exception {
        TestMediaDataSource dataSource = setDataSource("video_dovi_1920x1080_60fps_dvav_09.mp4");

        assertTrue("There should be either 1 or 2 tracks",
            0 < mExtractor.getTrackCount() && 3 > mExtractor.getTrackCount());

        MediaFormat trackFormat = mExtractor.getTrackFormat(0);
        int trackCountForDolbyVision = 1;

        // Handle the case where there is a Dolby Vision extractor
        // Note that it may or may not have a Dolby Vision Decoder
        if (mExtractor.getTrackCount() == 2) {
            if (trackFormat.getString(MediaFormat.KEY_MIME)
                    .equalsIgnoreCase(MIMETYPE_VIDEO_DOLBY_VISION)) {
                trackFormat = mExtractor.getTrackFormat(1);
                trackCountForDolbyVision = 0;
            }
        }

        if (advertisesDolbyVision()) {
            assertEquals("There must be 2 tracks", 2, mExtractor.getTrackCount());

            MediaFormat trackFormatForDolbyVision =
                mExtractor.getTrackFormat(trackCountForDolbyVision);

            final String mimeType = trackFormatForDolbyVision.getString(MediaFormat.KEY_MIME);
            assertEquals("video/dolby-vision", mimeType);

            int profile = trackFormatForDolbyVision.getInteger(MediaFormat.KEY_PROFILE);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe, profile);

            int level = trackFormatForDolbyVision.getInteger(MediaFormat.KEY_LEVEL);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60, level);

            final int trackIdForDolbyVision =
                trackFormatForDolbyVision.getInteger(MediaFormat.KEY_TRACK_ID);

            final int trackIdForBackwardCompat = trackFormat.getInteger(MediaFormat.KEY_TRACK_ID);
            assertEquals(trackIdForDolbyVision, trackIdForBackwardCompat);
        }

        // The backward-compatible track should have mime video/avc
        final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);
        assertEquals("video/avc", mimeType);
    }

    // DolbyVisionMediaExtractor for profile-level (Dvav1 10.0/Uhd30)
    @SmallTest
    @CddTest(requirement="5.3.8")
    public void testDolbyVisionMediaExtractorProfileDvav1() throws Exception {
        TestMediaDataSource dataSource = setDataSource("video_dovi_3840x2160_30fps_dav1_10.mp4");

        if (advertisesDolbyVision()) {
            assertEquals(1, mExtractor.getTrackCount());

            // Dvav1 10 exposes a single backward compatible track.
            final MediaFormat trackFormat = mExtractor.getTrackFormat(0);
            final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);

            assertEquals("video/dolby-vision", mimeType);

            final int profile = trackFormat.getInteger(MediaFormat.KEY_PROFILE);
            final int level = trackFormat.getInteger(MediaFormat.KEY_LEVEL);

            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvav110, profile);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd30, level);
        } else {
            MediaUtils.skipTest("Device does not provide a Dolby Vision decoder");
        }
    }

    // DolbyVisionMediaExtractor for profile-level (Dvav1 10.1/Uhd30)
    @SmallTest
    @CddTest(requirement="5.3.8")
    public void testDolbyVisionMediaExtractorProfileDvav1_2() throws Exception {
        TestMediaDataSource dataSource = setDataSource("video_dovi_3840x2160_30fps_dav1_10_2.mp4");

        assertTrue("There should be either 1 or 2 tracks",
            0 < mExtractor.getTrackCount() && 3 > mExtractor.getTrackCount());

        MediaFormat trackFormat = mExtractor.getTrackFormat(0);
        int trackCountForDolbyVision = 1;

        // Handle the case where there is a Dolby Vision extractor
        // Note that it may or may not have a Dolby Vision Decoder
        if (mExtractor.getTrackCount() == 2) {
            if (trackFormat.getString(MediaFormat.KEY_MIME)
                    .equalsIgnoreCase(MIMETYPE_VIDEO_DOLBY_VISION)) {
                trackFormat = mExtractor.getTrackFormat(1);
                trackCountForDolbyVision = 0;
            }
        }

        if (advertisesDolbyVision()) {
            assertEquals("There must be 2 tracks", 2, mExtractor.getTrackCount());

            MediaFormat trackFormatForDolbyVision =
                mExtractor.getTrackFormat(trackCountForDolbyVision);

            final String mimeType = trackFormatForDolbyVision.getString(MediaFormat.KEY_MIME);
            assertEquals("video/dolby-vision", mimeType);

            int profile = trackFormatForDolbyVision.getInteger(MediaFormat.KEY_PROFILE);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvav110, profile);

            int level = trackFormatForDolbyVision.getInteger(MediaFormat.KEY_LEVEL);
            assertEquals(MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd30, level);

            final int trackIdForDolbyVision =
                trackFormatForDolbyVision.getInteger(MediaFormat.KEY_TRACK_ID);

            final int trackIdForBackwardCompat = trackFormat.getInteger(MediaFormat.KEY_TRACK_ID);
            assertEquals(trackIdForDolbyVision, trackIdForBackwardCompat);
        }

        // The backward-compatible track should have mime video/av01
        final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);
        assertEquals("video/av01", mimeType);
    }

    //MPEG-H 3D Audio single stream (mha1)
    public void testMpegh3dAudioMediaExtractorMha1() throws Exception {
        // TODO(b/186267251) move file to cloud storage.
        AssetFileDescriptor afd = getContext().getResources()
            .openRawResourceFd(R.raw.sample_mpegh_mha1);
        mExtractor.setDataSource(afd);
        assertEquals(1, mExtractor.getTrackCount());

        // The following values below require API Build.VERSION_CODES.S
        if (!MediaUtils.check(IS_AT_LEAST_S, "test needs Android 12")) return;

        MediaFormat trackFormat = mExtractor.getTrackFormat(0);
        final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);
        assertEquals(MediaFormat.MIMETYPE_AUDIO_MPEGH_MHA1, mimeType);

        final int hpli = trackFormat.getInteger(MediaFormat.KEY_MPEGH_PROFILE_LEVEL_INDICATION);
        assertEquals(0x0D, hpli);

        final int hrcl = trackFormat.getInteger(MediaFormat.KEY_MPEGH_REFERENCE_CHANNEL_LAYOUT);
        assertEquals(0x13, hrcl);
    }

    //MPEG-H 3D Audio single stream encapsulated in MHAS (mhm1)
    public void testMpegh3dAudioMediaExtractorMhm1() throws Exception {
        // TODO(b/186267251) move file to cloud storage.
        AssetFileDescriptor afd = getContext().getResources()
            .openRawResourceFd(R.raw.sample_mpegh_mhm1);
        mExtractor.setDataSource(afd);
        assertEquals(1, mExtractor.getTrackCount());

        // The following values below require API Build.VERSION_CODES.S
        if (!MediaUtils.check(IS_AT_LEAST_S, "test needs Android 12")) return;

        MediaFormat trackFormat = mExtractor.getTrackFormat(0);
        final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);
        assertEquals(MediaFormat.MIMETYPE_AUDIO_MPEGH_MHM1, mimeType);

        final int hpli = trackFormat.getInteger(MediaFormat.KEY_MPEGH_PROFILE_LEVEL_INDICATION);
        assertEquals(0x0D, hpli);

        final int hrcl = trackFormat.getInteger(MediaFormat.KEY_MPEGH_REFERENCE_CHANNEL_LAYOUT);
        assertEquals(0x13, hrcl);

        final ByteBuffer hcos = trackFormat.getByteBuffer(MediaFormat.KEY_MPEGH_COMPATIBLE_SETS);
        assertEquals(0x12, hcos.get());
    }

    public void testGetDrmInitData() throws Exception {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        Preconditions.assertTestFileExists(mInpPrefix + "psshtest.mp4");
        setDataSource("psshtest.mp4");
        DrmInitData drmInitData = mExtractor.getDrmInitData();
        assertEquals(drmInitData.getSchemeInitDataCount(), 2);
        assertEquals(drmInitData.getSchemeInitDataAt(0).uuid, UUID_WIDEVINE);
        assertEquals(drmInitData.get(UUID_WIDEVINE), drmInitData.getSchemeInitDataAt(0));
        assertEquals(drmInitData.getSchemeInitDataAt(1).uuid, UUID_PLAYREADY);
        assertEquals(drmInitData.get(UUID_PLAYREADY), drmInitData.getSchemeInitDataAt(1));
    }

    private void checkExtractorSamplesAndMetrics() {
        // 1MB is enough for any sample.
        final ByteBuffer buf = ByteBuffer.allocate(1024*1024);
        final int trackCount = mExtractor.getTrackCount();

        for (int i = 0; i < trackCount; i++) {
            mExtractor.selectTrack(i);
        }

        for (int i = 0; i < trackCount; i++) {
            assertTrue(mExtractor.readSampleData(buf, 0) > 0);
            assertTrue(mExtractor.advance());
        }

        // verify some getMetrics() behaviors while we're here.
        PersistableBundle metrics = mExtractor.getMetrics();
        if (metrics == null) {
            fail("getMetrics() returns no data");
        } else {
            // ensure existence of some known fields
            int tracks = metrics.getInt(MediaExtractor.MetricsConstants.TRACKS, -1);
            if (tracks != trackCount) {
                fail("getMetrics() trackCount expect " + trackCount + " got " + tracks);
            }
        }
    }

    static boolean audioPresentationSetMatchesReference(
            Map<Integer, AudioPresentation> reference,
            List<AudioPresentation> actual) {
        if (reference.size() != actual.size()) {
            Log.w(TAG, "AudioPresentations set size is invalid, expected: " +
                    reference.size() + ", actual: " + actual.size());
            return false;
        }
        for (AudioPresentation ap : actual) {
            AudioPresentation refAp = reference.get(ap.getPresentationId());
            if (refAp == null) {
                Log.w(TAG, "AudioPresentation not found in the reference set, presentation id=" +
                        ap.getPresentationId());
                return false;
            }
            if (!refAp.equals(ap)) {
                Log.w(TAG, "AudioPresentations are different, reference: " +
                        refAp + ", actual: " + ap);
                return false;
            }
        }
        return true;
    }

    public void testGetAudioPresentations() throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix +
                        "MultiLangPerso_1PID_PC0_Select_AC4_H265_DVB_50fps_Audio_Only.ts");
        setDataSource("MultiLangPerso_1PID_PC0_Select_AC4_H265_DVB_50fps_Audio_Only.ts");
        int ac4TrackIndex = -1;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (MediaFormat.MIMETYPE_AUDIO_AC4.equals(mime)) {
                ac4TrackIndex = i;
                break;
            }
        }

        // Not all devices support AC4.
        if (ac4TrackIndex == -1) {
            List<AudioPresentation> presentations =
                    mExtractor.getAudioPresentations(0 /*trackIndex*/);
            assertNotNull(presentations);
            assertTrue(presentations.isEmpty());
            return;
        }

        // The test file has two sets of audio presentations. The presentation set
        // changes for every 100 audio presentation descriptors between two presentations.
        // Instead of attempting to count the presentation descriptors, the test assumes
        // a particular order of the presentations and advances to the next reference set
        // once getAudioPresentations returns a set that doesn't match the current reference set.
        // Thus the test can match the set 0 several times, then it encounters set 1,
        // advances the reference set index, matches set 1 until it encounters set 2 etc.
        // At the end it verifies that all the reference sets were met.
        List<Map<Integer, AudioPresentation>> refPresentations = Arrays.asList(
                new HashMap<Integer, AudioPresentation>() {{  // First set.
                    put(10, new AudioPresentation.Builder(10)
                            .setLocale(ULocale.ENGLISH)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasDialogueEnhancement(true)
                            .build());
                    put(11, new AudioPresentation.Builder(11)
                            .setLocale(ULocale.ENGLISH)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasAudioDescription(true)
                            .setHasDialogueEnhancement(true)
                            .build());
                    put(12, new AudioPresentation.Builder(12)
                            .setLocale(ULocale.FRENCH)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasDialogueEnhancement(true)
                            .build());
                }},
                new HashMap<Integer, AudioPresentation>() {{  // Second set.
                    put(10, new AudioPresentation.Builder(10)
                            .setLocale(ULocale.GERMAN)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasAudioDescription(true)
                            .setHasDialogueEnhancement(true)
                            .build());
                    put(11, new AudioPresentation.Builder(11)
                            .setLocale(new ULocale("es"))
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasSpokenSubtitles(true)
                            .setHasDialogueEnhancement(true)
                            .build());
                    put(12, new AudioPresentation.Builder(12)
                            .setLocale(ULocale.ENGLISH)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasDialogueEnhancement(true)
                            .build());
                }},
                null,
                null
        );
        refPresentations.set(2, refPresentations.get(0));
        refPresentations.set(3, refPresentations.get(1));
        boolean[] presentationsMatched = new boolean[refPresentations.size()];
        mExtractor.selectTrack(ac4TrackIndex);
        for (int i = 0; i < refPresentations.size(); ) {
            List<AudioPresentation> presentations = mExtractor.getAudioPresentations(ac4TrackIndex);
            assertNotNull(presentations);
            // Assumes all presentation sets have the same number of presentations.
            assertEquals(refPresentations.get(i).size(), presentations.size());
            if (!audioPresentationSetMatchesReference(refPresentations.get(i), presentations)) {
                    // Time to advance to the next presentation set.
                    i++;
                    continue;
            }
            Log.d(TAG, "Matched presentation " + i);
            presentationsMatched[i] = true;
            // No need to wait for another switch after the last presentation has been matched.
            if (i == presentationsMatched.length - 1 || !mExtractor.advance()) {
                break;
            }
        }
        for (int i = 0; i < presentationsMatched.length; i++) {
            assertTrue("Presentation set " + i + " was not found in the stream",
                    presentationsMatched[i]);
        }
    }

    @AppModeFull(reason = "Instant apps cannot bind sockets.")
    public void SKIP_testExtractorGetCachedDuration() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$SetDataSourceTest#testUrlDataSource
        CtsTestServer foo = new CtsTestServer(getContext());
        String url = foo.getAssetUrl("ringer.mp3");
        mExtractor.setDataSource(url);
        long cachedDurationUs = mExtractor.getCachedDuration();
        assertTrue("cached duration should be non-negative", cachedDurationUs >= 0);
        foo.shutdown();
    }

    public void SKIP_testExtractorHasCacheReachedEndOfStream() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$SetDataSourceTest#testUrlDataSource
        // Using file source to get deterministic result.
        AssetFileDescriptor afd = getAssetFileDescriptorFor("testvideo.3gp");
        mExtractor.setDataSource(afd);
        assertTrue(mExtractor.hasCacheReachedEndOfStream());
        afd.close();
    }

    /*
     * Makes sure if PTS(order) of a video file with BFrames matches the expected values in
     * the corresponding text file with just PTS values.
     */
    public void SKIP_testVideoPresentationTimeStampsMatch() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$ExtractorTimeStampTest
        setDataSource("binary_counter_320x240_30fps_600frames.mp4");
        // Select the only video track present in the file.
        final int trackCount = mExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            mExtractor.selectTrack(i);
        }

        Reader txtRdr = new BufferedReader(new InputStreamReader(new FileInputStream(
                mInpPrefix + "timestamps_binary_counter_320x240_30fps_600frames.txt")));
        StreamTokenizer strTok = new StreamTokenizer(txtRdr);
        strTok.parseNumbers();

        boolean srcAdvance = false;
        long srcSampleTimeUs = -1;
        long testSampleTimeUs = -1;

        strTok.nextToken();
        do {
            srcSampleTimeUs = mExtractor.getSampleTime();
            testSampleTimeUs = (long) strTok.nval;

            // Ignore round-off error if any.
            if (Math.abs(srcSampleTimeUs - testSampleTimeUs) > 1) {
                Log.d(TAG, "srcSampleTimeUs:" + srcSampleTimeUs + " testSampleTimeUs:" +
                        testSampleTimeUs);
                fail("video presentation timestamp not equal");
            }

            srcAdvance = mExtractor.advance();
            strTok.nextToken();
        } while (srcAdvance);
    }

    /* package */ static class ByteBufferDataSource extends MediaDataSource {
        private final long mSize;
        private TreeMap<Long, ByteBuffer> mMap = new TreeMap<Long, ByteBuffer>();

        public ByteBufferDataSource(MediaCodecTest.ByteBufferStream bufferStream)
                throws IOException {
            long size = 0;
            while (true) {
                final ByteBuffer buffer = bufferStream.read();
                if (buffer == null) break;
                final int limit = buffer.limit();
                if (limit == 0) continue;
                size += limit;
                mMap.put(size - 1, buffer); // key: last byte of validity for the buffer.
            }
            mSize = size;
        }

        @Override
        public long getSize() {
            return mSize;
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) {
            Log.v(TAG, "reading at " + position + " offset " + offset + " size " + size);

            // This chooses all buffers with key >= position (e.g. valid buffers)
            final SortedMap<Long, ByteBuffer> map = mMap.tailMap(position);
            int copied = 0;
            for (Map.Entry<Long, ByteBuffer> e : map.entrySet()) {
                // Get a read-only version of the byte buffer.
                final ByteBuffer bb = e.getValue().asReadOnlyBuffer();
                // Convert read position to an offset within that byte buffer, bboffs.
                final long bboffs = position - e.getKey() + bb.limit() - 1;
                if (bboffs >= bb.limit() || bboffs < 0) {
                    break; // (negative position)?
                }
                bb.position((int)bboffs); // cast is safe as bb.limit is int.
                final int tocopy = Math.min(size, bb.remaining());
                if (tocopy == 0) {
                    break; // (size == 0)?
                }
                bb.get(buffer, offset, tocopy);
                copied += tocopy;
                size -= tocopy;
                offset += tocopy;
                position += tocopy;
                if (size == 0) {
                    break; // finished copying.
                }
            }
            if (copied == 0) {
                copied = -1;  // signal end of file
            }
            return copied;
        }

        @Override
        public void close() {
            mMap = null;
        }
    }

    /* package */ static class MediaExtractorStream
                extends MediaCodecTest.ByteBufferStream implements Closeable {
        public boolean mIsFloat;
        public boolean mSawOutputEOS;
        public MediaFormat mFormat;

        private MediaExtractor mExtractor;
        private MediaCodecTest.MediaCodecStream mDecoderStream;

        public MediaExtractorStream(
                String inMime, String outMime,
                MediaDataSource dataSource) throws Exception {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(dataSource);
            final int numTracks = mExtractor.getTrackCount();
            // Single track?
            // assertEquals("Number of tracks should be 1", 1, numTracks);
            for (int i = 0; i < numTracks; ++i) {
                final MediaFormat format = mExtractor.getTrackFormat(i);
                final String actualMime = format.getString(MediaFormat.KEY_MIME);
                mExtractor.selectTrack(i);
                mFormat = format;
                if (outMime.equals(actualMime)) {
                    break;
                } else { // no matching mime, try to use decoder
                    mDecoderStream = new MediaCodecTest.MediaCodecStream(
                            mExtractor, mFormat);
                    Log.w(TAG, "fallback to input mime type with decoder");
                }
            }
            assertNotNull("MediaExtractor cannot find mime type " + inMime, mFormat);
            mIsFloat = mFormat.getInteger(
                    MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                            == AudioFormat.ENCODING_PCM_FLOAT;
        }

        public MediaExtractorStream(
                String inMime, String outMime,
                MediaCodecTest.ByteBufferStream inputStream) throws Exception {
            this(inMime, outMime, new ByteBufferDataSource(inputStream));
        }

        @Override
        public ByteBuffer read() throws IOException {
            if (mSawOutputEOS) {
                return null;
            }
            if (mDecoderStream != null) {
                return mDecoderStream.read();
            }
            // To preserve codec-like behavior, we create ByteBuffers
            // equal to the media sample size.
            final long size = mExtractor.getSampleSize();
            if (size >= 0) {
                final ByteBuffer inputBuffer = ByteBuffer.allocate((int)size);
                final int red = mExtractor.readSampleData(inputBuffer, 0 /* offset */); // sic
                if (red >= 0) {
                    assertEquals("position must be zero", 0, inputBuffer.position());
                    assertEquals("limit must be read bytes", red, inputBuffer.limit());
                    mExtractor.advance();
                    return inputBuffer;
                }
            }
            mSawOutputEOS = true;
            return null;
        }

        @Override
        public void close() throws IOException {
            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
            mFormat = null;
        }

        @Override
        protected void finalize() throws Throwable {
            if (mExtractor != null) {
                Log.w(TAG, "MediaExtractorStream wasn't closed");
                mExtractor.release();
            }
            mFormat = null;
        }
    }

    @SmallTest
    public void SKIP_testFlacIdentity() throws Exception {
        // duplicate of CtsMediaV2TestCases:CodecEncoderTest$testLosslessEncodeDecode[audio/flac]
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FusedExtractorDecoderTest[audio/flac]
        final int PCM_FRAMES = 1152 * 4; // FIXME: requires 4 flac frames to work with OMX codecs.
        final int CHANNEL_COUNT = 2;
        final int SAMPLES = PCM_FRAMES * CHANNEL_COUNT;
        final int[] SAMPLE_RATES = {44100, 192000}; // ensure 192kHz supported.

        for (int sampleRate : SAMPLE_RATES) {
            final MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_FLAC);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);

            Log.d(TAG, "Trying sample rate: " + sampleRate
                    + " channel count: " + CHANNEL_COUNT);
            format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5);

            // TODO: Add float mode when MediaExtractor supports float configuration.
            final MediaCodecTest.PcmAudioBufferStream audioStream =
                    new MediaCodecTest.PcmAudioBufferStream(
                            SAMPLES, sampleRate, 1000 /* frequency */, 100 /* sweep */,
                          false /* useFloat */);

            final MediaCodecTest.MediaCodecStream rawToFlac =
                    new MediaCodecTest.MediaCodecStream(
                            new MediaCodecTest.ByteBufferInputStream(audioStream),
                            format, true /* encode */);
            final MediaExtractorStream flacToRaw =
                    new MediaExtractorStream(MediaFormat.MIMETYPE_AUDIO_FLAC /* inMime */,
                            MediaFormat.MIMETYPE_AUDIO_RAW /* outMime */, rawToFlac);

            // Note: the existence of signed zero (as well as NAN) may make byte
            // comparisons invalid for floating point output. In our case, since the
            // floats come through integer to float conversion, it does not matter.
            assertEquals("Audio data not identical after compression",
                audioStream.sizeInBytes(),
                MediaCodecTest.compareStreams(new MediaCodecTest.ByteBufferInputStream(flacToRaw),
                    new MediaCodecTest.ByteBufferInputStream(
                            new MediaCodecTest.PcmAudioBufferStream(audioStream))));
        }
    }

    public void SKIP_testFlacMovExtraction() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FusedExtractorDecoderTest[audio/flac]
        AssetFileDescriptor testFd = getAssetFileDescriptorFor("sinesweepalac.mov");
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        testFd.close();
        extractor.selectTrack(0);
        boolean lastAdvanceResult = true;
        boolean lastReadResult = true;
        ByteBuffer buf = ByteBuffer.allocate(2*1024*1024);
        int totalSize = 0;
        while(true) {
            int n = extractor.readSampleData(buf, 0);
            if (n > 0) {
                totalSize += n;
            }
            if (!extractor.advance()) {
                break;
            }
        }
        assertTrue("could not read alac mov", totalSize > 0);
    }

    public void testProgramStreamExtraction() throws Exception {
        AssetFileDescriptor testFd = getAssetFileDescriptorFor("programstream.mpeg");

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        testFd.close();
        assertEquals("There must be 2 tracks", 2, extractor.getTrackCount());
        extractor.selectTrack(0);
        extractor.selectTrack(1);
        boolean lastAdvanceResult = true;
        boolean lastReadResult = true;
        int [] bytesRead = new int[2];
        MediaCodec [] codecs = { null, null };

        try {
            MediaFormat f = extractor.getTrackFormat(0);
            codecs[0] = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME));
            codecs[0].configure(f, null /* surface */, null /* crypto */, 0 /* flags */);
            codecs[0].start();
        } catch (IOException | IllegalArgumentException e) {
            // ignore
        }
        try {
            MediaFormat f = extractor.getTrackFormat(1);
            codecs[1] = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME));
            codecs[1].configure(f, null /* surface */, null /* crypto */, 0 /* flags */);
            codecs[1].start();
        } catch (IOException | IllegalArgumentException e) {
            // ignore
        }

        final int RETRY_LIMIT = 100;
        final long INPUTBUFFER_TIMEOUT_US = 10000;
        int num_retry = 0;
        ByteBuffer buf = ByteBuffer.allocate(2*1024*1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while(num_retry < RETRY_LIMIT) {
            for (MediaCodec codec : codecs) {
                if (codec == null) {
                    continue;
                }
                while (true) {
                    int idx = codec.dequeueOutputBuffer(info, 0);
                    if (idx < 0) {
                        break;
                    }
                    codec.releaseOutputBuffer(idx, false);
                }
            }

            int trackIdx = extractor.getSampleTrackIndex();
            MediaCodec codec = codecs[trackIdx];
            ByteBuffer b = buf;
            int bufIdx = -1;
            if (codec != null) {
                bufIdx = codec.dequeueInputBuffer(INPUTBUFFER_TIMEOUT_US);
                // No available input buffer now, retry again.
                if (bufIdx < 0) {
                    num_retry += 1;
                    continue;
                }

                num_retry = 0;
                b = codec.getInputBuffer(bufIdx);
            }
            int n = extractor.readSampleData(b, 0);
            if (n > 0) {
                bytesRead[trackIdx] += n;
            }
            if (codec != null) {
                int sampleFlags = extractor.getSampleFlags();
                long sampleTime = extractor.getSampleTime();
                codec.queueInputBuffer(bufIdx, 0, n, sampleTime, sampleFlags);
            }
            if (!extractor.advance()) {
                break;
            }
        }
        extractor.release();

        assertTrue("dequeueing input buffer exceeded timeout", num_retry < RETRY_LIMIT);
        assertTrue("did not read from track 0", bytesRead[0] > 0);
        assertTrue("did not read from track 1", bytesRead[1] > 0);
    }

    private void doTestAdvance(final String res) throws Exception {
        AssetFileDescriptor testFd = getAssetFileDescriptorFor(res);

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        testFd.close();
        extractor.selectTrack(0);
        boolean lastAdvanceResult = true;
        boolean lastReadResult = true;
        ByteBuffer buf = ByteBuffer.allocate(2*1024*1024);
        while(lastAdvanceResult || lastReadResult) {
            int n = extractor.readSampleData(buf, 0);
            if (lastAdvanceResult) {
                // previous advance() was successful, so readSampleData() should succeed
                assertTrue("readSampleData() failed after successful advance()", n >= 0);
                assertTrue("getSampleTime() failed after succesful advance()",
                        extractor.getSampleTime() >= 0);
                assertTrue("getSampleSize() failed after succesful advance()",
                        extractor.getSampleSize() >= 0);
                assertTrue("getSampleTrackIndex() failed after succesful advance()",
                        extractor.getSampleTrackIndex() >= 0);
            } else {
                // previous advance() failed, so readSampleData() should fail too
                assertTrue("readSampleData() succeeded after failed advance()", n < 0);
                assertTrue("getSampleTime() succeeded after failed advance()",
                        extractor.getSampleTime() < 0);
                assertTrue("getSampleSize() succeeded after failed advance()",
                        extractor.getSampleSize() < 0);
                assertTrue("getSampleTrackIndex() succeeded after failed advance()",
                        extractor.getSampleTrackIndex() < 0);
            }
            lastReadResult = (n >= 0);
            lastAdvanceResult = extractor.advance();
        }
        extractor.release();
    }

    public void SKIP_testAdvance() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$SetDataSourceTest and
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest#testExtract[*]
        // audio-only
        doTestAdvance("sinesweepm4a.m4a");
        doTestAdvance("sinesweepmp3lame.mp3");
        doTestAdvance("sinesweepmp3smpb.mp3");
        doTestAdvance("sinesweepwav.wav");
        doTestAdvance("sinesweepflac.flac");
        doTestAdvance("sinesweepogg.ogg");
        doTestAdvance("sinesweepoggmkv.mkv");

        // video-only
        doTestAdvance("swirl_144x136_mpeg4.mp4");
        doTestAdvance("video_640x360_mp4_hevc_450kbps_no_b.mp4");

        // audio+video
        doTestAdvance("video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz.mp4");
        doTestAdvance("video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    private void readAllData() {
        // 1MB is enough for any sample.
        final ByteBuffer buf = ByteBuffer.allocate(1024*1024);
        final int trackCount = mExtractor.getTrackCount();

        for (int i = 0; i < trackCount; i++) {
            mExtractor.selectTrack(i);
        }
        do {
            mExtractor.readSampleData(buf, 0);
        } while (mExtractor.advance());
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
        do {
            mExtractor.readSampleData(buf, 0);
        } while (mExtractor.advance());
    }

    public void SKIP_testAC3inMP4() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest[audio/ac3]
        setDataSource("testac3mp4.mp4");
        readAllData();
    }

    public void SKIP_testEAC3inMP4() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest[audio/eac3]
        setDataSource("testeac3mp4.mp4");
        readAllData();
    }

    public void SKIP_testAC3inTS() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest[audio/ac3]
        setDataSource("testac3ts.ts");
        readAllData();
    }

    public void SKIP_testEAC3inTS() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest[audio/eac3]
        setDataSource("testeac3ts.ts");
        readAllData();
    }

    public void SKIP_testAC4inMP4() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest[audio/ac4]
        setDataSource("multi0.mp4");
        readAllData();
    }

    public void testAV1InMP4() throws Exception {
        setDataSource("video_dovi_3840x2160_30fps_dav1_10_2.mp4");
        readAllData();
    }

    public void testDolbyVisionInMP4() throws Exception {
        setDataSource("video_dovi_3840x2160_30fps_dav1_10.mp4");
        readAllData();
    }

    public void testPcmLeInMov() throws Exception {
        setDataSource("sinesweeppcmlemov.mov");
        readAllData();
    }

    public void testPcmBeInMov() throws Exception {
        setDataSource("sinesweeppcmbemov.mov");
        readAllData();
    }

    public void testFragmentedRead() throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + "psshtest.mp4");
        setDataSource("psshtest.mp4");
        readAllData();
    }

    @AppModeFull(reason = "Instant apps cannot bind sockets.")
    public void testFragmentedHttpRead() throws Exception {
        CtsTestServer server = new CtsTestServer(getContext());
        Preconditions.assertTestFileExists(mInpPrefix + "psshtest.mp4");
        String url = server.getAssetUrl(mInpPrefix + "psshtest.mp4");
        mExtractor.setDataSource(url);
        readAllData();
        server.shutdown();
    }
}
