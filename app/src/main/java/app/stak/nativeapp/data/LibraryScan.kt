package app.stak.nativeapp.data

data class LibraryScanSnapshot(val state:String,val percentage:Float?=null,val resultKey:String?=null,val resultStatus:String?=null){val running get()=state in setOf("Running","Cancelling")}
data class LibraryScanState(val phase:String="idle",val progress:Float?=null,val message:String?=null,val allowed:Boolean?=null,val baseline:String?=null,val requested:Boolean=false,val sawRunning:Boolean=false){val active get()=phase in setOf("requesting","queued","running")}
fun reduceLibraryScan(old:LibraryScanState,next:LibraryScanSnapshot):LibraryScanState {
    if(next.running)return old.copy(phase="running",progress=next.percentage?.takeIf{it.isFinite()}?.div(100)?.coerceIn(0f,1f),message=if(next.state=="Cancelling")"Cancelling scan…"else "Scanning library…",sawRunning=true,baseline=if(!old.requested&&!old.sawRunning)next.resultKey else old.baseline)
    if(next.state!="Idle")return old.copy(progress=null,message="Scan status is not reported by the server.")
    val changed=next.resultKey!=null&&next.resultKey!=old.baseline
    if((old.requested||old.sawRunning)&&changed){
        return when(next.resultStatus){
            "Completed"->old.copy(phase="completed",progress=1f,message="Library scan complete",requested=false,sawRunning=false,baseline=next.resultKey)
            "Cancelled","Aborted"->old.copy(phase="stopped",progress=null,message="Library scan cancelled",requested=false,sawRunning=false,baseline=next.resultKey)
            "Failed"->old.copy(phase="failed",progress=null,message="Library scan failed on the server",requested=false,sawRunning=false,baseline=next.resultKey)
            else->old.copy(phase="queued",progress=null,message="Waiting for the server's scan result…")
        }
    }
    return if(old.requested||old.sawRunning)old.copy(phase="queued",progress=null,message="Waiting for Jellyfin to start the scan…")else old.copy(phase=if(old.phase=="completed")"completed"else "idle",baseline=next.resultKey)
}
interface LibraryScanGateway {suspend fun allowed():Boolean;suspend fun snapshot():LibraryScanSnapshot;suspend fun request()}
object DirectLibraryScanGateway:LibraryScanGateway {
    override suspend fun allowed()=JellyfinIntelligence.policy().get("IsAdministrator")?.asBoolean==true
    override suspend fun request()=JellyfinIntelligence.scanLibraries()
    override suspend fun snapshot():LibraryScanSnapshot {
        val rows=DirectJellyfin.sessionValue("/ScheduledTasks",expectedScope=jellyfinScope()).asJsonArray
        val task=rows.firstOrNull{it.isJsonObject&&it.asJsonObject.text("Key")=="RefreshLibrary"}?.asJsonObject ?: error("Library scan task was not reported.")
        val result=task.objectOrNull("LastExecutionResult")
        return LibraryScanSnapshot(task.text("State") ?: "Unknown",task.number("CurrentProgressPercentage")?.toFloat(),result?.text("EndTimeUtc") ?: result?.text("StartTimeUtc"),result?.text("Status"))
    }
}
