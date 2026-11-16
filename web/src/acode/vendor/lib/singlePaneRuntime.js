import {EditorView,hasHoverTooltips,closeHoverTooltips} from '@codemirror/view';
import {EditorSelection} from '@codemirror/state';
import {executeCommand} from 'cm/commandRegistry';
import {focusEditorIfEditable,blurEditorIfReadOnly} from 'cm/editorReadOnly';
import {isShiftSelectionActive as resolveShiftSelectionActive,isMultiCursorSelectionActive as resolveMultiCursorSelectionActive} from 'cm/shiftSelection';
import createPaneTouchSelectionMenu from 'cm/touchSelectionMenu';
import ScrollBar from 'components/scrollbar';
import quickTools from 'components/quickTools';
import keyboardHandler,{keydownState} from 'handlers/keyboard';
import {getSystemConfiguration,HARDKEYBOARDHIDDEN_NO} from 'lib/systemConfiguration';
import appSettings from 'lib/settings';
import {scrollbarOverflow} from '../../host/scrollbarMetrics.js';
import {editorDiagnosticLog} from '../../../logging/EditorDiagnostics.js';
import {runAutomaticCursorReveal,recordPipeline,tracePipelineDuration} from '../../../logging/EditorPipelineDiagnostics.js';

export function createSinglePaneRuntime($container){
 let editor,touchSelectionController,touchSelectionSyncRaf=0;
 const manager=globalThis.editorManager;
 const pane={editor:null,touchSelectionController:null,activeFile:manager.activeFile};
 const activePane=pane;
 const getActivePane=()=>pane;
 const setActivePane=p=>{if(p!==pane)throw new Error('This host has one editor pane');};
 let preventScrollbarV=false,preventScrollbarH=false,isScrolling=false;
 let lastScrollTop=0,lastScrollLeft=0,suppressCursorRevealUntil=0;
 let scrollbarScrollLockUntil=0,scrollbarScrollLockTop=null,scrollbarScrollLockLeft=null;
 let nativeContextMenuDisabled=null;
 let scrollRestoreFrame=0,scrollRestoreNestedFrame=0,scrollRestoreTimeout=0;
 const $vScrollbar=ScrollBar({parent:$container,placement:'right',width:20,thumbHeight:50,onscroll:onscrollV,onscrollend:onscrollVend});
 const $hScrollbar=ScrollBar({parent:$container,placement:'bottom',width:20,thumbHeight:50,onscroll:onscrollH,onscrollend:onscrollHEnd});
const setNativeContextMenuDisabled = (disabled) => {
		const value = !!disabled;
		if (nativeContextMenuDisabled === value) return;
		nativeContextMenuDisabled = value;
		const api = globalThis.system?.setNativeContextMenuDisabled;
		if (typeof api !== "function") return;
		try {
			api.call(globalThis.system, value);
		} catch (error) {
			console.warn("Failed to update native context menu state", error);
		}
	};
const pointerCursorVisibilityExtension = EditorView.updateListener.of(
		(update) => {
			if (!update.transactions.length) return;
			const pointerTriggered = update.transactions.some(
				(tr) =>
					tr.isUserEvent("pointer") ||
					tr.isUserEvent("select") ||
					tr.isUserEvent("select.pointer") ||
					tr.isUserEvent("touch") ||
					tr.isUserEvent("select.touch"),
			);
			if (!pointerTriggered) {
				clearScrollbarScrollLock();
				return;
			}
			if (!update.selectionSet) return;
			requestAnimationFrame(() => {
				if (isCursorRevealSuppressed()) return;
				if (!isCursorVisible()) scrollCursorIntoView({ behavior: "instant" });
			});
		},
	);
const isShiftClickSelectionEnabled = () =>
		appSettings.value.shiftClickSelection !== false;
const isShiftSelectionActive = (event) => {
		return resolveShiftSelectionActive({
			event,
			quickToolsShift: quickTools?.$footer?.dataset?.shift != null,
			shiftClickSelection: isShiftClickSelectionEnabled(),
		});
	};
const isMultiCursorSelectionActive = (event) => {
		return resolveMultiCursorSelectionActive({
			event,
			quickToolsCtrl: quickTools?.$footer?.dataset?.ctrl != null,
			quickToolsMeta: quickTools?.$footer?.dataset?.meta != null,
		});
	};
const isQuickToolsMultiCursorSelectionActive = () => {
		return resolveMultiCursorSelectionActive({
			quickToolsCtrl: quickTools?.$footer?.dataset?.ctrl != null,
			quickToolsMeta: quickTools?.$footer?.dataset?.meta != null,
			isMac: false,
		});
	};
const shiftClickSelectionExtension = EditorView.domEventHandlers({
		mousedown(event, view) {
			if (!event.shiftKey || isShiftClickSelectionEnabled()) return false;
			if ((event.button ?? 0) !== 0) return false;

			const pos = view.posAtCoords(
				{ x: event.clientX, y: event.clientY },
				false,
			);
			if (pos == null) return false;

			view.dispatch({
				selection: EditorSelection.cursor(pos),
				userEvent: "select.pointer",
			});
			focusEditorIfEditable(view);
			event.preventDefault();
			return true;
		},
		click(event) {
			if (!touchSelectionController?.consumePendingShiftSelectionClick(event)) {
				return false;
			}
			event.preventDefault();
			return true;
		},
	});
const multiCursorSelectionExtension = EditorView.clickAddsSelectionRange.of(
		isMultiCursorSelectionActive,
	);
const touchSelectionUpdateExtension = EditorView.updateListener.of(
		(update) => {
			if (!touchSelectionController) return;
			const pointerTriggered = update.transactions.some(
				(tr) =>
					tr.isUserEvent("pointer") ||
					tr.isUserEvent("select") ||
					tr.isUserEvent("select.pointer") ||
					tr.isUserEvent("touch") ||
					tr.isUserEvent("select.touch"),
			);
			if (update.selectionSet || pointerTriggered) {
				cancelAnimationFrame(touchSelectionSyncRaf);
				touchSelectionSyncRaf = requestAnimationFrame(() => {
					touchSelectionController?.onStateChanged({
						pointerTriggered,
						selectionChanged: update.selectionSet,
					});
				});
			}
		},
	);
function revealEditorRange(
		targetEditor,
		from,
		to = from,
		{ y = "center", userEvent = "select.reveal" } = {},
	) {
		if (!targetEditor) return false;

		try {
			const length = targetEditor.state.doc.length;
			const anchor = Math.max(0, Math.min(Number(from) || 0, length));
			const head = Math.max(0, Math.min(Number(to) || 0, length));

			if (targetEditor === editor) {
				cancelPendingScrollRestore();
				clearScrollbarScrollLock();
			}

			targetEditor.dispatch({
				selection: { anchor, head },
				effects: EditorView.scrollIntoView(anchor, { y }),
				userEvent,
			});
			focusEditorIfEditable(targetEditor);
			return true;
		} catch (error) {
			console.error("Error revealing editor range:", error);
			return false;
		}
	}
function cancelPendingScrollRestore() {
		if (scrollRestoreFrame) {
			cancelAnimationFrame(scrollRestoreFrame);
			scrollRestoreFrame = 0;
		}
		if (scrollRestoreNestedFrame) {
			cancelAnimationFrame(scrollRestoreNestedFrame);
			scrollRestoreNestedFrame = 0;
		}
		if (scrollRestoreTimeout) {
			clearTimeout(scrollRestoreTimeout);
			scrollRestoreTimeout = 0;
		}
	}
function registerSoftKeyboardCursorReveal() {
		const shouldRevealCursor = () => {
			const view = editor;
			if (!view || manager?.activeFile?.type !== "editor") return false;
			const activeElement = document.activeElement;
			return (
				view.contentDOM === activeElement ||
				view.contentDOM.contains(activeElement)
			);
		};

		keyboardHandler.on("keyboardShowStart", () => {
			requestAnimationFrame(() => {
				if (!shouldRevealCursor()) return;
				if (isCursorRevealSuppressed()) return;
				scrollCursorIntoView({ behavior: "instant" });
			});
		});
		keyboardHandler.on("keyboardShow", () => {
			if (!shouldRevealCursor()) return;
			if (isCursorRevealSuppressed()) return;
			scrollCursorIntoView();
		});
		keyboardHandler.on("keyboardHide", () => {
			requestAnimationFrame(() => {
				if (!shouldRevealCursor()) return;
				if (isCursorRevealSuppressed()) return;
				scrollCursorIntoView({ behavior: "instant" });
			});
		});
	}
function scrollCursorIntoView(options = {}) {
		const view = editor;
		const scroller = view?.scrollDOM;
		if (!view || !scroller) return;

		const { behavior = "smooth" } = options;
		const { head } = view.state.selection.main;
		const caret = safeCoordsAtPos(view, head);
		if (!caret) return;

		const scrollerRect = scroller.getBoundingClientRect();
		const relativeTop = caret.top - scrollerRect.top + scroller.scrollTop;
		const relativeBottom = caret.bottom - scrollerRect.top + scroller.scrollTop;
		const topMargin = 16;
		const bottomMargin = 24;

		const scrollTop = scroller.scrollTop;
		const visibleTop = scrollTop + topMargin;
		const visibleBottom = scrollTop + scroller.clientHeight - bottomMargin;

		if (relativeTop < visibleTop) {
			const nextTop = Math.max(relativeTop - topMargin, 0);
			scroller.scrollTo({ top: nextTop, behavior });
		} else if (relativeBottom > visibleBottom) {
			const delta = relativeBottom - visibleBottom;
			scroller.scrollTo({ top: scrollTop + delta, behavior });
		}
	}
function suppressCursorReveal(duration = 500) {
		suppressCursorRevealUntil = Date.now() + duration;
	}
function isCursorRevealSuppressed() {
		return Date.now() < suppressCursorRevealUntil;
	}
function lockScrollbarScrollPosition({ top, left }, duration = 1200) {
		const scroller = editor?.scrollDOM;
		if (!scroller) return;
		scrollbarScrollLockUntil = Date.now() + duration;
		if (typeof top === "number") scrollbarScrollLockTop = top;
		if (typeof left === "number") scrollbarScrollLockLeft = left;
	}
function clearScrollbarScrollLock() {
		scrollbarScrollLockUntil = 0;
		scrollbarScrollLockTop = null;
		scrollbarScrollLockLeft = null;
	}
function restoreScrollbarScrollLock() {
		if (Date.now() >= scrollbarScrollLockUntil) {
			clearScrollbarScrollLock();
			return false;
		}

		const scroller = editor?.scrollDOM;
		if (!scroller) return false;

		let restored = false;
		if (
			typeof scrollbarScrollLockTop === "number" &&
			Math.abs(scroller.scrollTop - scrollbarScrollLockTop) > 1
		) {
			scroller.scrollTop = scrollbarScrollLockTop;
			lastScrollTop = scroller.scrollTop;
			restored = true;
		}
		if (
			typeof scrollbarScrollLockLeft === "number" &&
			Math.abs(scroller.scrollLeft - scrollbarScrollLockLeft) > 1
		) {
			scroller.scrollLeft = scrollbarScrollLockLeft;
			lastScrollLeft = scroller.scrollLeft;
			restored = true;
		}
		return restored;
	}
function isCursorVisible() {
		const view = editor;
		const scroller = view?.scrollDOM;
		if (!view || !scroller) return true;

		const { head } = view.state.selection.main;
		const caret = safeCoordsAtPos(view, head);
		if (!caret) return true;

		const scrollerRect = scroller.getBoundingClientRect();
		return caret.top >= scrollerRect.top && caret.bottom <= scrollerRect.bottom;
	}
function safeCoordsAtPos(view, pos) {
		try {
			return view.coordsAtPos(pos);
		} catch (_) {
			return null;
		}
	}
function onscrollV(value) {
		const scroller = editor?.scrollDOM;
		if (!scroller) return;
		suppressCursorReveal();
		const normalized = clamp01(value);
		const maxScroll = Math.max(
			scroller.scrollHeight - scroller.clientHeight,
			0,
		);
		preventScrollbarV = true;
		scroller.scrollTop = normalized * maxScroll;
		lastScrollTop = scroller.scrollTop;
		lockScrollbarScrollPosition({ top: lastScrollTop });
	}
function onscrollVend() {
		suppressCursorReveal(1200);
		lockScrollbarScrollPosition({ top: editor?.scrollDOM?.scrollTop }, 1200);
		preventScrollbarV = false;
		setVScrollValue();
	}
function onscrollH(value) {
		if (appSettings.value.textWrap) return;
		const scroller = editor?.scrollDOM;
		if (!scroller) return;
		suppressCursorReveal();
		const normalized = clamp01(value);
		const maxScroll = Math.max(scroller.scrollWidth - scroller.clientWidth, 0);
		preventScrollbarH = true;
		scroller.scrollLeft = normalized * maxScroll;
		lastScrollLeft = scroller.scrollLeft;
		lockScrollbarScrollPosition({ left: lastScrollLeft });
	}
function onscrollHEnd() {
		suppressCursorReveal(1200);
		lockScrollbarScrollPosition({ left: editor?.scrollDOM?.scrollLeft }, 1200);
		preventScrollbarH = false;
		setHScrollValue();
	}
function setHScrollValue() {
		if (appSettings.value.textWrap || preventScrollbarH) return;
		const scroller = editor?.scrollDOM;
		if (!scroller) return;
		const maxScroll = Math.max(scroller.scrollWidth - scroller.clientWidth, 0);
		if (maxScroll <= 0) {
			lastScrollLeft = 0;
			$hScrollbar.value = 0;
			return;
		}
		const scrollLeft = scroller.scrollLeft;
		if (scrollLeft === lastScrollLeft) return;
		lastScrollLeft = scrollLeft;
		const factor = scrollLeft / maxScroll;
		$hScrollbar.value = clamp01(factor);
	}
function onscrollleft() {
		if (appSettings.value.textWrap) {
			$hScrollbar.hide();
			return;
		}
		const scroller = editor?.scrollDOM;
		if (!scroller) return;
		const maxScroll = Math.max(scroller.scrollWidth - scroller.clientWidth, 0);
		if (maxScroll <= 0) {
			$hScrollbar.hide();
			lastScrollLeft = 0;
			$hScrollbar.value = 0;
			return;
		}
		setHScrollValue();
		$hScrollbar.render();
	}
function readScrollMetrics() {
		const scroller = editor?.scrollDOM;
		if (!scroller) return null;
		return {
			scrollTop: scroller.scrollTop,
			scrollLeft: scroller.scrollLeft,
			scrollHeight: scroller.scrollHeight,
			scrollWidth: scroller.scrollWidth,
			clientHeight: scroller.clientHeight,
			clientWidth: scroller.clientWidth,
		};
	}
function updateScrollbarsFromMetrics(metrics, sourcePane) {
		if (!metrics) return;
		if (sourcePane !== activePane || sourcePane.activeFile?.type !== "editor") {
			if (sourcePane === activePane) {
				$hScrollbar.hideImmediately();
				$vScrollbar.hideImmediately();
			}
			return;
		}

		const maxScrollTop = Math.max(
			metrics.scrollHeight - metrics.clientHeight,
			0,
		);
		if (maxScrollTop <= 0) {
			$vScrollbar.hide();
			lastScrollTop = 0;
			$vScrollbar.value = 0;
		} else {
			if (!preventScrollbarV && metrics.scrollTop !== lastScrollTop) {
				lastScrollTop = metrics.scrollTop;
				$vScrollbar.value = clamp01(metrics.scrollTop / maxScrollTop);
			}
			$vScrollbar.render();
		}

		if (appSettings.value.textWrap) {
			$hScrollbar.hide();
			return;
		}

		const maxScrollLeft = Math.max(
			metrics.scrollWidth - metrics.clientWidth,
			0,
		);
		if (maxScrollLeft <= 0) {
			$hScrollbar.hide();
			lastScrollLeft = 0;
			$hScrollbar.value = 0;
			return;
		}

		if (!preventScrollbarH && metrics.scrollLeft !== lastScrollLeft) {
			lastScrollLeft = metrics.scrollLeft;
			$hScrollbar.value = clamp01(metrics.scrollLeft / maxScrollLeft);
		}
		$hScrollbar.render();
	}
function setVScrollValue() {
		if (preventScrollbarV) return;
		const scroller = editor?.scrollDOM;
		if (!scroller) return;
		const maxScroll = Math.max(
			scroller.scrollHeight - scroller.clientHeight,
			0,
		);
		if (maxScroll <= 0) {
			lastScrollTop = 0;
			$vScrollbar.value = 0;
			return;
		}
		const scrollTop = scroller.scrollTop;
		if (scrollTop === lastScrollTop) return;
		lastScrollTop = scrollTop;
		const factor = scrollTop / maxScroll;
		$vScrollbar.value = clamp01(factor);
	}
function onscrolltop() {
		const scroller = editor?.scrollDOM;
		if (!scroller) return;
		const maxScroll = Math.max(
			scroller.scrollHeight - scroller.clientHeight,
			0,
		);
		if (maxScroll <= 0) {
			$vScrollbar.hide();
			lastScrollTop = 0;
			$vScrollbar.value = 0;
			return;
		}
		setVScrollValue();
		$vScrollbar.render();
	}
function clamp01(value) {
		if (value <= 0) return 0;
		if (value >= 1) return 1;
		return value;
	}
async function setupEditor(pane = getActivePane()) {
		const editor = pane?.editor;
		const touchSelectionController = pane?.touchSelectionController;
		if (!pane || !editor) return;
		const settings = appSettings.value;
		const { colorPreview, fontSize, lineHeight } = appSettings.value;

		let scrollTimeout;
		let scrollSyncRaf = 0;
		const scroller = editor.scrollDOM;
		let pendingKeyboardHideBlur = null;
		pane.cleanupEditorListeners?.();
		pane.cleanupEditorListeners = null;

		function syncScrollUi() {
			if (pane !== activePane) return;
			scrollSyncRaf = 0;
			if (pane.activeFile?.type !== "editor") {
				$hScrollbar.hideImmediately();
				$vScrollbar.hideImmediately();
				return;
			}
			editor.requestMeasure({
				read: () => readScrollMetrics(),
				write: (metrics) => updateScrollbarsFromMetrics(metrics, pane),
			});
		}

		function handleEditorScroll() {
			if (pane !== activePane) return;
			if (!scroller) return;
			if (restoreScrollbarScrollLock()) return;
			if (!isScrolling) {
				isScrolling = true;
				if (hasHoverTooltips(editor.state)) {
					editor.dispatch({ effects: closeHoverTooltips });
				}
				touchSelectionController?.onScrollStart();
			}
			if (!scrollSyncRaf) {
				scrollSyncRaf = requestAnimationFrame(syncScrollUi);
			}
			clearTimeout(scrollTimeout);
			scrollTimeout = setTimeout(() => {
				isScrolling = false;
				touchSelectionController?.onScrollEnd();
			}, 100);
		}

		scroller?.addEventListener("scroll", handleEditorScroll, { passive: true });
		function onScrollInteraction() {
			clearScrollbarScrollLock();
			// Refresh on gesture start as well as actual scroll: a hidden
			// WebView may not produce its first scroll event after reactivation.
			if (!scrollSyncRaf) scrollSyncRaf = requestAnimationFrame(syncScrollUi);
		}
		for (const type of ["pointerdown", "touchstart", "touchmove", "wheel"])
			scroller?.addEventListener(type, onScrollInteraction, { passive: true });
		syncScrollUi();

		// Attach native DOM event listeners directly to the editor's contentDOM
		const contentDOM = editor.contentDOM;
		let readOnlyNativeContextActive = false;
		let readOnlyNativeContextResetTimer = null;
		const isReadOnlyEditor = () => {
			const activeFile = pane.activeFile;
			return (
				editor.state.readOnly ||
				activeFile?.editable === false ||
				!!activeFile?.loading
			);
		};
		const clearFileFocusState = () => {
			const activeFile = pane.activeFile;
			if (!activeFile) return;
			activeFile.focused = false;
			activeFile.focusedBefore = false;
		};
		const clearPendingKeyboardHideBlur = () => {
			if (!pendingKeyboardHideBlur) return;
			keyboardHandler.off("keyboardHide", pendingKeyboardHideBlur);
			pendingKeyboardHideBlur = null;
		};
		const releaseReadOnlyNativeContext = () => {
			clearTimeout(readOnlyNativeContextResetTimer);
			readOnlyNativeContextResetTimer = null;
			if (!readOnlyNativeContextActive) return;
			readOnlyNativeContextActive = false;
			setNativeContextMenuDisabled(false);
		};
		const scheduleReadOnlyNativeContextRelease = () => {
			if (!readOnlyNativeContextActive) return;
			clearTimeout(readOnlyNativeContextResetTimer);
			readOnlyNativeContextResetTimer = setTimeout(
				releaseReadOnlyNativeContext,
				800,
			);
		};
		function handleContentPointerDown() {
			if (!isReadOnlyEditor()) return;
			clearTimeout(readOnlyNativeContextResetTimer);
			readOnlyNativeContextResetTimer = null;
			readOnlyNativeContextActive = true;
			setNativeContextMenuDisabled(true);
		}
		function handleDocumentPointerDown(event) {
			if (!readOnlyNativeContextActive) return;
			if (event.target instanceof Node && editor.dom.contains(event.target)) {
				return;
			}
			releaseReadOnlyNativeContext();
		}
		const isFocused =
			contentDOM === document.activeElement ||
			contentDOM.contains(document.activeElement);
		setNativeContextMenuDisabled(isFocused);

		function handleContentFocus(_event) {
			setActivePane(pane);
			if (isReadOnlyEditor()) {
				if (!readOnlyNativeContextActive) {
					setNativeContextMenuDisabled(false);
				}
				clearPendingKeyboardHideBlur();
				clearFileFocusState();
				blurEditorIfReadOnly(editor, true);
				touchSelectionController?.onStateChanged();
				return;
			}
			setNativeContextMenuDisabled(true);
			const activeFile = pane.activeFile;
			if (activeFile) {
				activeFile.focused = true;
			}
			touchSelectionController?.onStateChanged();
		}

		async function handleContentBlur(_event) {
			if (!readOnlyNativeContextActive) {
				setNativeContextMenuDisabled(false);
			}
			touchSelectionController?.setMenu(false);
			if (isReadOnlyEditor()) {
				clearPendingKeyboardHideBlur();
				clearFileFocusState();
				return;
			}
			const { hardKeyboardHidden, keyboardHeight } =
				await getSystemConfiguration();
			const blur = clearFileFocusState;
			if (
				hardKeyboardHidden === HARDKEYBOARDHIDDEN_NO &&
				keyboardHeight < 100
			) {
				// external keyboard - blur immediately
				blur();
				return;
			}
			// soft keyboard - wait for keyboard to hide
			const onKeyboardHide = () => {
				keyboardHandler.off("keyboardHide", onKeyboardHide);
				if (pendingKeyboardHideBlur === onKeyboardHide) {
					pendingKeyboardHideBlur = null;
				}
				blur();
			};
			clearPendingKeyboardHideBlur();
			pendingKeyboardHideBlur = onKeyboardHide;
			keyboardHandler.on("keyboardHide", onKeyboardHide);
		}

		function handleContentKeydown(event) {
			if (event.key === "Escape") {
				keydownState.esc = { value: true, target: contentDOM };
			}
		}

		contentDOM.addEventListener("focus", handleContentFocus);
		contentDOM.addEventListener("blur", handleContentBlur);
		contentDOM.addEventListener("keydown", handleContentKeydown);
		contentDOM.addEventListener("pointerdown", handleContentPointerDown, true);
		document.addEventListener("pointerdown", handleDocumentPointerDown, true);
		document.addEventListener(
			"pointerup",
			scheduleReadOnlyNativeContextRelease,
			true,
		);
		document.addEventListener(
			"pointercancel",
			scheduleReadOnlyNativeContextRelease,
			true,
		);

		pane.cleanupEditorListeners = () => {
			scroller?.removeEventListener("scroll", handleEditorScroll);
			for (const type of ["pointerdown", "touchstart", "touchmove", "wheel"])
				scroller?.removeEventListener(type, onScrollInteraction);
			contentDOM.removeEventListener("focus", handleContentFocus);
			contentDOM.removeEventListener("blur", handleContentBlur);
			contentDOM.removeEventListener("keydown", handleContentKeydown);
			contentDOM.removeEventListener(
				"pointerdown",
				handleContentPointerDown,
				true,
			);
			document.removeEventListener(
				"pointerdown",
				handleDocumentPointerDown,
				true,
			);
			document.removeEventListener(
				"pointerup",
				scheduleReadOnlyNativeContextRelease,
				true,
			);
			document.removeEventListener(
				"pointercancel",
				scheduleReadOnlyNativeContextRelease,
				true,
			);
			releaseReadOnlyNativeContext();
			clearTimeout(scrollTimeout);
			if (scrollSyncRaf) {
				cancelAnimationFrame(scrollSyncRaf);
				scrollSyncRaf = 0;
			}
			if (pendingKeyboardHideBlur) {
				keyboardHandler.off("keyboardHide", pendingKeyboardHideBlur);
				pendingKeyboardHideBlur = null;
			}
		};

	}
 // Host policy surrounds the original function, whose body remains upstream.
 const upstreamCursorReveal=scrollCursorIntoView;
 scrollCursorIntoView=options=>runAutomaticCursorReveal(editor,options,()=>upstreamCursorReveal(options));
 // Cover both runtime.refresh and upstream setupEditor/syncScrollUi without
 // replacing request objects, their callbacks or the original function bodies.
 // Keep the upstream manager algorithm byte-for-byte. A parked WebView can
 // report 0x0 during first entry; such metrics must not hide the scrollbars.
 const coreUpdateScrollbars=updateScrollbarsFromMetrics;
 updateScrollbarsFromMetrics=(metrics,sourcePane)=>{
  if (!scrollbarOverflow(metrics,appSettings.value.textWrap)) return;
  return coreUpdateScrollbars(metrics,sourcePane);
 };
 const upstreamReadScrollMetrics=readScrollMetrics,upstreamUpdateScrollbars=updateScrollbarsFromMetrics;
 readScrollMetrics=()=>tracePipelineDuration('pipeline-scrollbar-read','scrollbarReads',upstreamReadScrollMetrics);
 updateScrollbarsFromMetrics=(metrics,sourcePane)=>{
  if(!editorDiagnosticLog.enabled)return upstreamUpdateScrollbars(metrics,sourcePane);
  return tracePipelineDuration('pipeline-scrollbar-write','scrollbarWrites',()=>upstreamUpdateScrollbars(metrics,sourcePane));
 };
 return {
  extensions:{pointerCursorVisibilityExtension,shiftClickSelectionExtension,multiCursorSelectionExtension,touchSelectionUpdateExtension},
  attach(view){editor=view;pane.editor=view;manager.editor=view;
   touchSelectionController=createPaneTouchSelectionMenu(view,{container:$container,getActiveFile:()=>manager.activeFile,isShiftSelectionActive,isMultiCursorSelectionActive:isQuickToolsMultiCursorSelectionActive});
   pane.touchSelectionController=touchSelectionController;
   editor.insert = function (text) {
		try {
			const { from, to } = editor.state.selection.main;
			const insertText = String(text ?? "");
			// Replace current selection and move cursor to end of inserted text
			editor.dispatch({
				changes: { from, to, insert: insertText },
				selection: {
					anchor: from + insertText.length,
					head: from + insertText.length,
				},
			});
			return true;
		} catch (_) {
			return false;
		}
	};
editor.execCommand = function (commandName, args) {
		if (!commandName) return false;
		return executeCommand(String(commandName), editor, args);
	};
editor.getCopyText = function () {
		try {
			const { from, to } = editor.state.selection.main;
			if (from === to) return ""; // No selection
			return editor.state.doc.sliceString(from, to);
		} catch (_) {
			return "";
		}
	};
editor.gotoLine = function (line, column = 0, animate = false) {
		try {
			const { state } = editor;
			const { doc } = state;

			let targetLine,
				targetColumn = column;

			// If line is a string, parse it for special formats
			if (typeof line === "string") {
				const match = /^([+-])?(\d+)?(:\d+)?(%)?$/.exec(line.trim());
				if (!match) {
					console.warn("Invalid gotoLine format:", line);
					return false;
				}

				const currentLine = doc.lineAt(state.selection.main.head);
				const [, sign, lineNum, colonColumn, percent] = match;

				// Parse column if specified in line:column format
				if (colonColumn) {
					targetColumn = Math.max(0, +colonColumn.slice(1) - 1); // Convert to 0-based
				}

				// Parse line number
				let parsedLine = lineNum ? +lineNum : currentLine.number;

				if (lineNum && percent) {
					// Percentage format: "50%" or "+10%"
					let percentage = parsedLine / 100;
					if (sign) {
						percentage =
							percentage * (sign === "-" ? -1 : 1) +
							currentLine.number / doc.lines;
					}
					targetLine = Math.round(doc.lines * percentage);
				} else if (lineNum && sign) {
					// Relative format: "+5" or "-3"
					targetLine =
						parsedLine * (sign === "-" ? -1 : 1) + currentLine.number;
				} else if (lineNum) {
					// Absolute line number
					targetLine = parsedLine;
				} else {
					// No line number specified, stay on current line
					targetLine = currentLine.number;
				}
			} else {
				// Simple numeric line parameter
				targetLine = line;
			}

			// Clamp line number to valid range
			const lineNum = Math.max(1, Math.min(targetLine, doc.lines));
			const docLine = doc.line(lineNum);

			// Clamp column to line length
			const col = Math.max(0, Math.min(targetColumn, docLine.length));
			const pos = docLine.from + col;

			return revealEditorRange(editor, pos);
		} catch (error) {
			console.error("Error in gotoLine:", error);
			return false;
		}
	};
editor.scrollToRow = function (row) {
		try {
			const scroller = editor.scrollDOM;
			if (!scroller) return false;

			if (row === Number.POSITIVE_INFINITY) {
				clearScrollbarScrollLock();
				scroller.scrollTop = Math.max(
					scroller.scrollHeight - scroller.clientHeight,
					0,
				);
				return true;
			}

			const parsedRow = Number(row);
			if (!Number.isFinite(parsedRow)) return false;
			const aceRow = Math.max(0, Math.floor(parsedRow));
			const lineNum = Math.min(editor.state.doc.lines, aceRow + 1);
			const line = editor.state.doc.line(lineNum);
			editor.dispatch({
				effects: EditorView.scrollIntoView(line.from, { y: "start" }),
			});
			return true;
		} catch (_) {
			return false;
		}
	};
editor.setSelection = function (value) {
		touchSelectionController?.setSelection(!!value);
	};
editor.setMenu = function (value) {
		touchSelectionController?.setMenu(!!value);
	};
   setupEditor(pane);registerSoftKeyboardCursorReveal();
  },
  get selectionMenu(){return touchSelectionController;},
  refresh(reason='runtime-refresh'){
   if(!editor)return;
   if(editorDiagnosticLog.enabled)recordPipeline('pipeline-refresh',{reason});
   editor.requestMeasure({read:readScrollMetrics,write:m=>updateScrollbarsFromMetrics(m,pane)});
  },
  destroy(){pane.cleanupEditorListeners?.();cancelAnimationFrame(touchSelectionSyncRaf);touchSelectionController?.destroy();$vScrollbar.destroy();$hScrollbar.destroy();$vScrollbar.remove();$hScrollbar.remove();},
 };
}
