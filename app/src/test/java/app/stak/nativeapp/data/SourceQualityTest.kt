package app.stak.nativeapp.data
import org.junit.Assert.*
import org.junit.Test
class SourceQualityTest {
    @Test fun resolutionOutranksSeedCountAndSize(){val hd=SourceRelease("1080p",resolution="1080p",seeders=999,sizeBytes=999999);val uhd=SourceRelease("2160p",resolution="2160p",seeders=1,sizeBytes=1);assertEquals(listOf(uhd,hd),bestQualityOrder(listOf(hd,uhd),emptySet(),false))}
    @Test fun remuxWinsWithinResolution(){val web=SourceRelease("Movie 2160p WEB-DL",resolution="2160p",webdl=true,dolbyVision=true);val remux=web.copy(name="Movie 2160p REMUX",remux=true,dolbyVision=false);assertEquals(remux,bestQualityOrder(listOf(web,remux),emptySet(),false).first())}
    @Test fun camIsNotPromotedByClaimedResolution(){val cam=SourceRelease("Movie HDCAM 2160p",resolution="2160p");val hd=SourceRelease("Movie 1080p WEB-DL",webdl=true);assertEquals(hd,bestQualityOrder(listOf(cam,hd),emptySet(),false).first())}
    @Test fun losslessAudioBreaksVisualQualityTies(){val lossy=SourceRelease("Movie 2160p DDP Atmos",resolution="2160p",atmos=true);val lossless=lossy.copy(name="Movie 2160p TrueHD Atmos");assertEquals(lossless,bestQualityOrder(listOf(lossy,lossless),emptySet(),false).first())}
    @Test fun cacheAndLanguageDoNotOverrideBetterQuality(){val hd=SourceRelease("1080p",infoHash="cached",languages=listOf("HUN"));val uhd=SourceRelease("2160p",resolution="2160p");assertEquals(uhd,bestQualityOrder(listOf(hd,uhd),setOf("cached"),true).first())}
    @Test fun equalReleasesRetainProviderOrder(){val a=SourceRelease("A",sizeBytes=1);val b=SourceRelease("B",sizeBytes=1000);assertEquals(listOf(a,b),bestQualityOrder(listOf(a,b),emptySet(),false))}
    @Test fun reasonsReflectOnlyKnownReleaseTraits(){assertEquals("4K · Remux · Dolby Vision · Lossless Atmos",sourceQualitySummary(SourceRelease("Movie 2160p TrueHD Atmos",resolution="2160p",remux=true,dolbyVision=true,atmos=true)))}
}
