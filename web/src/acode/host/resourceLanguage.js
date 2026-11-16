import {html} from '@codemirror/lang-html';
import {css} from '@codemirror/lang-css';
import {javascript} from '@codemirror/lang-javascript';
import {json} from '@codemirror/lang-json';
import {markdown} from '@codemirror/lang-markdown';
import {xml} from '@codemirror/lang-xml';

export function languageForFile(name, kind='resource') {
  if(kind==='notebook')return html({matchClosingTags:true,autoCloseTags:true});
  const ext=String(name).split('.').at(-1).toLowerCase();
  if(['html','htm','xhtml'].includes(ext))return html({matchClosingTags:true,autoCloseTags:true});
  if(['css','scss','less'].includes(ext))return css();
  if(['js','mjs','cjs','jsx','ts','tsx'].includes(ext))return javascript({typescript:['ts','tsx'].includes(ext),jsx:['jsx','tsx'].includes(ext)});
  if(['json','jsonc'].includes(ext))return json();
  if(['md','markdown'].includes(ext))return markdown();
  if(['xml','svg','xsd','xsl','xslt'].includes(ext))return xml();
  return [];
}
