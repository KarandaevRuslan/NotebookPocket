import quickTools from 'components/quickTools';
import quickToolsAdapters from 'lib/quickToolsAdapter';
import appSettings from 'lib/settings';
import actions from 'handlers/quickTools';
export function syncQuickToolsVisibility(file) {
	const { $toggler } = quickTools;
	const hideForFile = !quickToolsAdapters.visible(file);

	clearTimeout($toggler._hideTimeout);
	if (hideForFile || !appSettings.value.floatingButton) {
		$toggler.classList.add("hide");
		$toggler._hideTimeout = setTimeout(() => {
			$toggler.remove();
			$toggler._hideTimeout = null;
		}, 300);
	} else {
		$toggler._hideTimeout = null;
		$toggler.classList.remove("hide");
		if (!$toggler.isConnected) {
			root.appendOuter($toggler);
		}
	}

	if (hideForFile) {
		actions("set-height", { height: 0, save: false });
		return;
	}

	const quickToolsHeight =
		appSettings.value.quickTools !== undefined
			? appSettings.value.quickTools
			: 1;
	actions("set-height", { height: quickToolsHeight, save: false });
}
