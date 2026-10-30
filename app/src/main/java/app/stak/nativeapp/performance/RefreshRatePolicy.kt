package app.stak.nativeapp.performance

import android.os.Build
import android.view.Window

data class DisplayChoice(val id:Int,val width:Int,val height:Int,val hz:Float)
fun preferredSmoothMode(modes:List<DisplayChoice>,width:Int,height:Int):DisplayChoice? {
    val same=modes.filter{it.width==width&&it.height==height&&it.hz.isFinite()&&it.hz>0}
    return same.filter{it.hz>=89f}.minByOrNull{kotlin.math.abs(it.hz-90f)} ?: same.maxByOrNull{it.hz}
}
fun requestSmoothDisplay(window:Window) {
    val display=window.decorView.display ?: return
    val current=display.mode
    val choice=preferredSmoothMode(display.supportedModes.map{DisplayChoice(it.modeId,it.physicalWidth,it.physicalHeight,it.refreshRate)},current.physicalWidth,current.physicalHeight) ?: return
    val attributes=window.attributes
    val modeId=if(Build.VERSION.SDK_INT>=34)0 else choice.id
    if(attributes.preferredDisplayModeId==modeId&&kotlin.math.abs(attributes.preferredRefreshRate-choice.hz)<.1f)return
    attributes.preferredDisplayModeId=modeId
    attributes.preferredRefreshRate=choice.hz
    window.attributes=attributes
}
