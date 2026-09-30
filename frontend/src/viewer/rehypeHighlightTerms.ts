import { matchRanges } from '../search/queryTerms';

/**
 * The shape of a hast node this transform touches. It is declared here rather than imported so the
 * viewer depends on nothing but the plugins it was given.
 */
type HastNode = {
  type: string;
  value?: string;
  tagName?: string;
  properties?: Record<string, unknown>;
  children?: HastNode[];
};

export type HighlightOptions = { readonly terms: readonly string[] };

/**
 * Wraps every matched term of the rendered Markdown in a `<mark>` node. It rewrites the tree that
 * react-markdown is about to render, which is how the viewer highlights without ever turning a
 * response into HTML: no raw HTML plugin, no string handed to the DOM as markup.
 */
export function rehypeHighlightTerms(options: HighlightOptions) {
  return (tree: HastNode): void => {
    if (options.terms.length > 0) {
      highlightChildren(tree, options.terms);
    }
  };
}

function highlightChildren(node: HastNode, terms: readonly string[]): void {
  if (!node.children) {
    return;
  }

  const rewritten: HastNode[] = [];
  for (const child of node.children) {
    if (child.type === 'text' && typeof child.value === 'string') {
      rewritten.push(...splitAroundMatches(child.value, terms));
      continue;
    }
    highlightChildren(child, terms);
    rewritten.push(child);
  }
  node.children = rewritten;
}

function splitAroundMatches(text: string, terms: readonly string[]): HastNode[] {
  const ranges = matchRanges(text, terms);
  if (ranges.length === 0) {
    return [{ type: 'text', value: text }];
  }

  const nodes: HastNode[] = [];
  let cursor = 0;
  for (const range of ranges) {
    if (range.start > cursor) {
      nodes.push({ type: 'text', value: text.slice(cursor, range.start) });
    }
    nodes.push(markNode(text.slice(range.start, range.end)));
    cursor = range.end;
  }
  if (cursor < text.length) {
    nodes.push({ type: 'text', value: text.slice(cursor) });
  }

  return nodes;
}

function markNode(value: string): HastNode {
  return {
    type: 'element',
    tagName: 'mark',
    properties: {},
    children: [{ type: 'text', value }],
  };
}
