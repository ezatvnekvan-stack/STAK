import { PageTitle } from "../components/Bits";
import JellyfinLibrary from "../components/JellyfinLibrary";
import LibrarySwitch from "../components/LibrarySwitch";
import { useT } from "../lib/i18n";

export default function JellyfinLibraryPage() {
  const t = useT();
  return (
    <div className="stak-screen stak-screen-library collection-page">
      <PageTitle title={t("Library")} testId="jellyfin-library-title"/>
      <LibrarySwitch />
      <JellyfinLibrary />
    </div>
  );
}
