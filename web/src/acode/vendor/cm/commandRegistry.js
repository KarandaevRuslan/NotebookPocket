
import * as cmCommands from "@codemirror/commands";
import {
	cursorCharLeft,
	cursorCharRight,
	cursorDocEnd,
	cursorDocStart,
	cursorGroupLeft,
	cursorGroupRight,
	cursorLineDown,
	cursorLineEnd,
	cursorLineStart,
	cursorLineUp,
	cursorMatchingBracket,
	cursorPageDown,
	cursorPageUp,
	deleteCharBackward,
	deleteCharForward,
	deleteGroupBackward,
	deleteGroupForward,
	deleteLineBoundaryForward,
	deleteToLineEnd,
	deleteToLineStart,
	indentLess,
	indentMore,
	indentSelection,
	insertBlankLine,
	insertNewlineAndIndent,
	lineComment,
	lineUncomment,
	redo,
	selectAll,
	selectCharLeft,
	selectCharRight,
	selectDocEnd,
	selectDocStart,
	selectGroupLeft,
	selectGroupRight,
	selectLine,
	selectLineDown,
	selectLineEnd,
	selectLineStart,
	selectLineUp,
	selectMatchingBracket,
	selectPageDown,
	selectPageUp,
	simplifySelection,
	toggleBlockComment,
	toggleLineComment,
	undo,
} from "@codemirror/commands";
import {
	foldCode,
	indentUnit as indentUnitFacet,
	unfoldCode,
} from "@codemirror/language";


import { Compartment, EditorSelection } from "@codemirror/state";
import { keymap } from "@codemirror/view";
import { focusEditorIfEditable } from "cm/editorReadOnly";
import {
	copyLineDownFoldAware,
	copyLineUpFoldAware,
	deleteLineFoldAware,
	moveLineDownFoldAware,
	moveLineUpFoldAware,
} from "cm/foldAwareLineCommands";
import { foldAllCodeBlocks, unfoldAllCodeBlocks } from "cm/foldingCommands";
import {
	canonicalizeKeyBinding,
	keyBindingsConflict,
	toCodeMirrorKey,
} from "cm/keyBindingUtils";





import actions from "handlers/quickTools";
import keyBindings, {
	APP_KEY_BINDING_NAMES,
	CODEMIRROR_COMMAND_NAMES,
} from "lib/keyBindings";
import settings from "lib/settings";


const commandKeymapCompartment = new Compartment();

/**
 * @typedef {import("@codemirror/view").EditorView} EditorView
 */

/**
 * @typedef {{
 *  name: string;
 *  description?: string;
 *  readOnly?: boolean;
 *  run: (view?: EditorView | null) => boolean | void;
 *  requiresView?: boolean;
 *  defaultDescription?: string;
 *  defaultKey?: string | null;
 *  key?: string | null;
 * }} CommandEntry
 */

/** @type {Map<string, CommandEntry>} */
const commandMap = new Map();

/** @type {Record<string, any>} */
let resolvedKeyBindings = keyBindings;

/** @type {Record<string, any>} */
let cachedResolvedKeyBindings = {};

/** @type {Record<string, any>} */
let cachedEffectiveKeyBindings = {};

/** @type {{key: string, command: string, shadowedBy: string}[]} */
let cachedKeyBindingConflicts = [];

let resolvedKeyBindingsVersion = 0;

/** @type {import("@codemirror/view").KeyBinding[]} */
let cachedKeymap = [];

/** @type {Set<EditorView>} */
const commandViews = new Set();

const CODEMIRROR_COMMAND_ENTRIES = Object.entries(cmCommands).filter(
	([name, value]) =>
		typeof value === "function" && CODEMIRROR_COMMAND_NAMES.has(name),
);

const CODEMIRROR_COMMAND_MAP = new Map(
	CODEMIRROR_COMMAND_ENTRIES.map(([name, fn]) => [name, fn]),
);

registerCoreCommands();


registerCommandsFromKeyBindings();
rebuildKeymap();

function registerCoreCommands() {
	addCommand({
		name: "focusEditor",
		description: "Focus editor",
		readOnly: true,
		requiresView: false,
		run(view) {
			const resolvedView = resolveView(view);
			if (resolvedView) focusEditorIfEditable(resolvedView);
			return true;
		},
	});
	
	
	
	
	
	
	
	
	
	
	
	
	addCommand({
		name: "saveFile",
		description: "Save current file",
		readOnly: true,
		requiresView: false,
		run() {
			acode.exec("save");
			return true;
		},
	});
	
	
	
	
	
	
	
	
	
	
	addCommand({
		name: "toggleFullscreen",
		description: "Toggle full screen mode",
		readOnly: true,
		requiresView: false,
		run() {
			acode.exec("toggle-fullscreen");
			return true;
		},
	});
	
	
	
	addCommand({
		name: "selectall",
		description: "Select all",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return selectAll(resolvedView);
		},
	});
	addCommand({
		name: "gotoline",
		description: "Go to line...",
		readOnly: true,
		requiresView: false,
		run() {
			acode.exec("goto");
			return true;
		},
	});
	addCommand({
		name: "find",
		description: "Find",
		readOnly: true,
		requiresView: false,
		run() {
			acode.exec("find");
			return true;
		},
	});
	addCommand({
		name: "copy",
		description: "Copy",
		readOnly: true,
		requiresView: true,
		run: copyCommand,
	});
	addCommand({
		name: "cut",
		description: "Cut",
		readOnly: false,
		requiresView: true,
		run: cutCommand,
	});
	addCommand({
		name: "paste",
		description: "Paste",
		readOnly: false,
		requiresView: true,
		run: pasteCommand,
	});

	
	
	addCommand({
		name: "replace",
		description: "Replace",
		readOnly: true,
		requiresView: false,
		run() {
			acode.exec("replace");
			return true;
		},
	});
	addCommand({
		name: "openCommandPalette",
		description: "Open command palette",
		readOnly: true,
		requiresView: false,
		run() {
			acode.exec("command-palette");
			return true;
		},
	});
	
	addCommand({
		name: "toggleQuickTools",
		description: "Toggle quick tools",
		readOnly: true,
		requiresView: false,
		run() {
			actions("toggle");
			return true;
		},
	});
	addCommand({
		name: "selectWord",
		description: "Select current word",
		readOnly: false,
		requiresView: true,
		run: selectWordCommand,
	});
	
	
	
	addCommand({
		name: "increaseFontSize",
		description: "Increase editor font size",
		readOnly: true,
		requiresView: false,
		run: () => adjustFontSize(1),
	});
	addCommand({
		name: "decreaseFontSize",
		description: "Decrease editor font size",
		readOnly: true,
		requiresView: false,
		run: () => adjustFontSize(-1),
	});
	
	
	
	
	
	
	
	
	
	

	// Additional editor-centric helpers mapped to CodeMirror primitives that have existing key bindings in defaults.
	addCommand({
		name: "duplicateSelection",
		description: "Duplicate selection",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return copyLineDownFoldAware(resolvedView);
		},
	});
	addCommand({
		name: "copylinesdown",
		description: "Copy lines down",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return copyLineDownFoldAware(resolvedView);
		},
	});
	addCommand({
		name: "copylinesup",
		description: "Copy lines up",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return copyLineUpFoldAware(resolvedView);
		},
	});
	addCommand({
		name: "movelinesdown",
		description: "Move lines down",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return moveLineDownFoldAware(resolvedView);
		},
	});
	addCommand({
		name: "movelinesup",
		description: "Move lines up",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return moveLineUpFoldAware(resolvedView);
		},
	});
	addCommand({
		name: "removeline",
		description: "Remove line",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return deleteLineFoldAware(resolvedView);
		},
	});
	addCommand({
		name: "insertlineafter",
		description: "Insert line after",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return insertBlankLine(resolvedView);
		},
	});
	addCommand({
		name: "selectline",
		description: "Select line",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return selectLine(resolvedView);
		},
	});
	addCommand({
		name: "selectlinesdown",
		description: "Select line down",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return selectLineDown(resolvedView);
		},
	});
	addCommand({
		name: "selectlinesup",
		description: "Select line up",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return selectLineUp(resolvedView);
		},
	});
	addCommand({
		name: "selectlinestart",
		description: "Select line start",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return selectLineStart(resolvedView);
		},
	});
	addCommand({
		name: "selectlineend",
		description: "Select line end",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return selectLineEnd(resolvedView);
		},
	});
	addCommand({
		name: "indent",
		description: "Indent",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			const { state } = resolvedView;
			const hasSelection = state.selection.ranges.some((range) => !range.empty);
			if (hasSelection) {
				return indentMore(resolvedView);
			}
			const indentString =
				state.facet(indentUnitFacet) ||
				(settings?.value?.softTab
					? " ".repeat(Math.max(1, Number(settings?.value?.tabSize) || 2))
					: "\t");
			const insert = indentString && indentString.length ? indentString : "\t";
			resolvedView.dispatch(
				state.changeByRange((range) => ({
					changes: { from: range.from, to: range.to, insert },
					range: EditorSelection.cursor(range.from + insert.length),
				})),
			);
			return true;
		},
	});
	addCommand({
		name: "outdent",
		description: "Outdent",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return indentLess(resolvedView);
		},
	});
	addCommand({
		name: "indentselection",
		description: "Indent selection",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return indentSelection(resolvedView);
		},
	});
	addCommand({
		name: "newline",
		description: "Insert newline",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return insertNewlineAndIndent(resolvedView);
		},
	});
	addCommand({
		name: "joinlines",
		description: "Join lines",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return deleteLineBoundaryForward(resolvedView);
		},
	});
	addCommand({
		name: "deletetolinestart",
		description: "Delete to line start",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return deleteToLineStart(resolvedView);
		},
	});
	addCommand({
		name: "deletetolineend",
		description: "Delete to line end",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return deleteToLineEnd(resolvedView);
		},
	});
	addCommand({
		name: "togglecomment",
		description: "Toggle comment",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return toggleLineComment(resolvedView);
		},
	});
	addCommand({
		name: "comment",
		description: "Add line comment",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return lineComment(resolvedView);
		},
	});
	addCommand({
		name: "uncomment",
		description: "Remove line comment",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return lineUncomment(resolvedView);
		},
	});
	addCommand({
		name: "toggleBlockComment",
		description: "Toggle block comment",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return toggleBlockComment(resolvedView);
		},
	});
	addCommand({
		name: "undo",
		description: "Undo",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return undo(resolvedView);
		},
	});
	addCommand({
		name: "redo",
		description: "Redo",
		readOnly: false,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return redo(resolvedView);
		},
	});
	addCommand({
		name: "simplifySelection",
		description: "Simplify selection",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return simplifySelection(resolvedView);
		},
	});
	addCommand({
		name: "foldCode",
		description: "Fold the Lines that are selected (if possible)",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return foldCode(resolvedView);
		},
	});
	addCommand({
		name: "unfoldCode",
		description: "Unfold folded ranges on selected lines.",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return unfoldCode(resolvedView);
		},
	});
	addCommand({
		name: "foldAll",
		description:
			"Fold all - top-level ranges usually depends on the syntax tree. It may not work reliably if the document isn't fully parsed (e.g., just initialized or too large to parse completely)",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return foldAllCodeBlocks(resolvedView);
		},
	});
	addCommand({
		name: "unfoldAll",
		description: "Unfold all folded code.",
		readOnly: true,
		requiresView: true,
		run(view) {
			const resolvedView = resolveView(view);
			if (!resolvedView) return false;
			return unfoldAllCodeBlocks(resolvedView);
		},
	});
}





function registerCommandsFromKeyBindings() {
	Object.entries(keyBindings).forEach(([name, binding]) => {
		if (commandMap.has(name) || APP_KEY_BINDING_NAMES.has(name)) return;
		const description = binding?.description || humanizeCommandName(name);
		const readOnly = binding?.readOnly ?? false;
		const requiresView = !!binding?.editorOnly;
		const commandFn = CODEMIRROR_COMMAND_MAP.get(name);

		if (binding?.action) {
			addCommand({
				name,
				description,
				readOnly,
				requiresView,
				run(view) {
					try {
						if (requiresView) {
							const resolvedView = resolveView(view);
							if (!resolvedView) return false;
						}
						acode.exec(binding.action);
						return true;
					} catch (error) {
						console.error(`Failed to execute action ${binding.action}`, error);
						return false;
					}
				},
			});
			return;
		}

		if (commandFn) {
			addCommand({
				name,
				description,
				readOnly,
				requiresView: true,
				run(view) {
					const resolvedView = resolveView(view);
					if (!resolvedView) return false;
					return commandFn(resolvedView);
				},
			});
		}
	});
}

function addCommand(entry) {
	const command = {
		...entry,
		defaultDescription: entry.description || entry.name,
		defaultKey: entry.key ?? null,
		key: entry.key ?? null,
	};
	commandMap.set(entry.name, command);
}

function resolveView(view) {
	return view || editorManager?.editor || null;
}





function humanizeCommandName(name) {
	return name
		.replace(/([a-z0-9])([A-Z])/g, "$1 $2")
		.replace(/_/g, " ")
		.replace(/^./, (char) => char.toUpperCase());
}

function copyCommand(view) {
	const resolvedView = resolveView(view);
	if (!resolvedView) return false;
	const { state } = resolvedView;
	const texts = state.selection.ranges.map((range) => {
		if (range.empty) {
			const line = state.doc.lineAt(range.head);
			return state.doc.sliceString(line.from, line.to);
		}
		return state.doc.sliceString(range.from, range.to);
	});
	const textToCopy = texts.join("\n");
	cordova.plugins.clipboard.copy(textToCopy);
	return true;
}

function cutCommand(view) {
	const resolvedView = resolveView(view);
	if (!resolvedView) return false;
	const { state } = resolvedView;
	const ranges = state.selection.ranges;
	const segments = [];
	let changes = [];
	ranges.forEach((range) => {
		if (range.empty) {
			const line = state.doc.lineAt(range.head);
			segments.push(state.doc.sliceString(line.from, line.to));
			changes.push({ from: line.from, to: line.to, insert: "" });
			return;
		}
		segments.push(state.doc.sliceString(range.from, range.to));
		changes.push({ from: range.from, to: range.to, insert: "" });
	});
	cordova.plugins.clipboard.copy(segments.join("\n"));
	resolvedView.dispatch({
		changes,
		selection: EditorSelection.single(
			changes[0]?.from ?? state.selection.main.from,
		),
		userEvent: "delete.cut",
		scrollIntoView: true,
	});
	return true;
}

function pasteCommand(view) {
	const resolvedView = resolveView(view);
	if (!resolvedView) return false;
	cordova.plugins.clipboard.paste((text = "") => {
		const insertText = String(text);
		resolvedView.dispatch(
			resolvedView.state.changeByRange((range) => ({
				changes: { from: range.from, to: range.to, insert: insertText },
				range: EditorSelection.cursor(range.from + insertText.length),
			})),
		);
	});
	return true;
}



function selectWordCommand(view) {
	const resolvedView = resolveView(view);
	if (!resolvedView) return false;
	const { state } = resolvedView;
	const ranges = state.selection.ranges.map((range) => {
		const word = state.wordAt(range.head);
		if (word) return EditorSelection.range(word.from, word.to);
		const line = state.doc.lineAt(range.head);
		return EditorSelection.range(line.from, line.to);
	});
	resolvedView.dispatch({
		selection: EditorSelection.create(ranges, state.selection.mainIndex),
	});
	return true;
}



function adjustFontSize(delta) {
	const current = settings?.value?.fontSize || "12px";
	const numeric = Number.parseInt(current, 10) || 12;
	const next = Math.min(72, Math.max(6, numeric + delta));
	settings.value.fontSize = `${next}px`;
	settings.update(false);
	return true;
}



function parseKeyString(keyString) {
	if (!keyString) return [];
	return String(keyString)
		.split("|")
		.map((combo) => combo.trim())
		.filter(Boolean);
}

function hasOwnBindingOverride(name) {
	return Object.prototype.hasOwnProperty.call(resolvedKeyBindings ?? {}, name);
}

function resolveBindingInfo(name) {
	const baseBinding = keyBindings[name] ?? null;
	if (!hasOwnBindingOverride(name)) return baseBinding;

	const override = resolvedKeyBindings?.[name];
	if (override === null) {
		return baseBinding ? { ...baseBinding, key: null } : { key: null };
	}

	if (!override || typeof override !== "object") {
		return baseBinding;
	}

	return baseBinding ? { ...baseBinding, ...override } : override;
}

function buildResolvedKeyBindingsSnapshot() {
	const bindingNames = new Set([
		...Object.keys(keyBindings),
		...Object.keys(resolvedKeyBindings ?? {}),
	]);

	return Object.fromEntries(
		Array.from(bindingNames, (name) => [name, resolveBindingInfo(name)]).filter(
			([, binding]) => binding,
		),
	);
}

function rebuildKeymap() {
	cachedResolvedKeyBindings = buildResolvedKeyBindingsSnapshot();
	const candidates = [];
	let order = 0;
	commandMap.forEach((command, name) => {
		const bindingInfo = resolveBindingInfo(name);
		command.description =
			bindingInfo?.description || command.defaultDescription;
		const keySource =
			bindingInfo && Object.prototype.hasOwnProperty.call(bindingInfo, "key")
				? bindingInfo.key
				: (command.defaultKey ?? null);
		command.key = keySource;
		const combos = parseKeyString(keySource);
		combos.forEach((combo) => {
			const cmKey = toCodeMirrorKey(combo);
			if (!cmKey) return;
			candidates.push({
				name,
				key: cmKey,
				rawKey: combo,
				order: order++,
				priority: getBindingPriority(name),
			});
		});
	});

	candidates.sort(
		(left, right) => right.priority - left.priority || left.order - right.order,
	);

	const bindings = [];
	const claimedKeys = new Map();
	const effectiveKeys = new Map();
	const conflicts = [];
	for (const candidate of candidates) {
		const canonicalKey = canonicalizeKeyBinding(candidate.key);
		const claimed = Array.from(claimedKeys.entries()).find(([key]) =>
			keyBindingsConflict(key, canonicalKey),
		);
		if (claimed) {
			const [claimedKey, owner] = claimed;
			const appCommandShadowsCodeMirrorDefault =
				APP_KEY_BINDING_NAMES.has(owner) &&
				CODEMIRROR_COMMAND_NAMES.has(candidate.name) &&
				candidate.priority === 10;
			const repeatedKeyForSameCommand =
				owner === candidate.name && claimedKey === canonicalKey;
			if (!repeatedKeyForSameCommand && !appCommandShadowsCodeMirrorDefault) {
				conflicts.push({
					key: candidate.key,
					command: candidate.name,
					shadowedBy: owner,
				});
			}
			continue;
		}

		claimedKeys.set(canonicalKey, candidate.name);
		if (!effectiveKeys.has(candidate.name)) {
			effectiveKeys.set(candidate.name, []);
		}
		effectiveKeys.get(candidate.name).push(candidate.rawKey);
		bindings.push({
			key: candidate.key,
			run: (view) => executeCommand(candidate.name, view),
			preventDefault: true,
		});
	}

	cachedEffectiveKeyBindings = Object.fromEntries(
		Object.entries(cachedResolvedKeyBindings).map(([name, binding]) => [
			name,
			{
				...binding,
				key: effectiveKeys.get(name)?.join("|") || null,
			},
		]),
	);
	cachedKeyBindingConflicts = conflicts;
	cachedKeymap = bindings;
	resolvedKeyBindingsVersion += 1;
	return bindings;
}

function getBindingPriority(name) {
	if (APP_KEY_BINDING_NAMES.has(name)) return 40;
	if (!Object.prototype.hasOwnProperty.call(keyBindings, name)) return 30;

	const override = resolvedKeyBindings?.[name];
	if (
		override &&
		typeof override === "object" &&
		Object.prototype.hasOwnProperty.call(override, "key") &&
		override.key !== keyBindings[name]?.key
	) {
		return 20;
	}

	return 10;
}

function resolveCommand(name) {
	return commandMap.get(name) || null;
}

function commandRunsInReadOnly(command, view) {
	if (!view) return command.readOnly;
	return view.state?.readOnly ? !!command.readOnly : true;
}

export function executeCommand(name, view, args) {
	const command = resolveCommand(name);
	if (!command) return false;
	const targetView = command.requiresView
		? resolveView(view)
		: resolveView(view) || null;
	if (command.requiresView && !targetView) return false;
	if (!commandRunsInReadOnly(command, targetView)) return false;
	const keyboardRequest = name === "cut" && targetView?.hasFocus ? globalThis.system?.beginClipboardCut?.() : null;
	try {
		const result = command.run(targetView, args);
		return result !== false;
	} catch (error) {
		console.error(`Failed to execute command ${name}`, error);
		return false;
	} finally {
		if (keyboardRequest != null) globalThis.system?.completeClipboardCut?.(keyboardRequest);
	}
}

export function getRegisteredCommands() {
	return Array.from(commandMap.values()).map((command) => ({
		name: command.name,
		description: command.description || command.defaultDescription,
		key: command.key || null,
	}));
}

export function getResolvedKeyBindings() {
	return cachedResolvedKeyBindings;
}

export function getEffectiveKeyBindings() {
	return cachedEffectiveKeyBindings;
}

export function getKeyBindingConflicts() {
	return cachedKeyBindingConflicts.map((conflict) => ({ ...conflict }));
}

export function getResolvedKeyBindingsVersion() {
	return resolvedKeyBindingsVersion;
}

export function getCommandKeymapExtension() {
	return commandKeymapCompartment.of(keymap.of(cachedKeymap));
}







export { commandKeymapCompartment };

export function registerExternalCommand(descriptor = {}) {
	const normalized = normalizeExternalCommand(descriptor);
	if (!normalized) return null;

	const { name } = normalized;
	if (commandMap.has(name)) {
		commandMap.delete(name);
	}

	addCommand(normalized);
	const stored = commandMap.get(name);
	if (stored) {
		stored.key = normalized.key ?? stored.key;
	}

	rebuildKeymap();
	return stored;
}

export function removeExternalCommand(name) {
	if (!name) return false;
	const exists = commandMap.has(name);
	if (!exists) return false;
	commandMap.delete(name);
	rebuildKeymap();
	return true;
}

export function refreshCommandKeymap(view) {
	const resolvedView = resolveView(view);
	applyCommandKeymap(resolvedView);
}

function normalizeExternalCommand(descriptor) {
	const name =
		typeof descriptor?.name === "string" ? descriptor.name.trim() : "";
	if (!name) {
		console.warn("Command registration skipped: missing name", descriptor);
		return null;
	}
	const exec = typeof descriptor?.exec === "function" ? descriptor.exec : null;
	if (!exec) {
		console.warn(
			`Command registration skipped for "${name}": exec must be a function.`,
		);
		return null;
	}

	const requiresView = descriptor?.requiresView ?? true;
	const key = normalizeExternalKey(descriptor?.bindKey);

	return {
		name,
		description: descriptor?.description || humanizeCommandName(name),
		readOnly: descriptor?.readOnly ?? true,
		requiresView,
		key,
		run(view, args) {
			try {
				const resolvedView = resolveView(view);
				if (requiresView && !resolvedView) return false;
				const result = exec(resolvedView || null, args);
				return result !== false;
			} catch (error) {
				console.error(`Command \"${name}\" failed`, error);
				return false;
			}
		},
	};
}

function normalizeExternalKey(bindKey) {
	if (!bindKey) return null;
	if (typeof bindKey === "string") return bindKey;
	const combos = [];
	if (typeof bindKey === "object") {
		const pushCombo = (combo) => {
			if (typeof combo === "string" && combo.trim()) combos.push(combo.trim());
		};
		pushCombo(bindKey.win);
		pushCombo(bindKey.linux);
		pushCombo(bindKey.mac);
	}
	return combos.length ? combos.join("|") : null;
}

function applyCommandKeymap(view, bindings = cachedKeymap) {
	if (!view) return;
	commandViews.add(view);
	view.dispatch({
		effects: commandKeymapCompartment.reconfigure(
			keymap.of(bindings ?? cachedKeymap),
		),
	});
}
