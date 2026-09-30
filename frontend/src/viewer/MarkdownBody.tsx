import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Markdown from 'react-markdown';
import rehypeSlug from 'rehype-slug';
import remarkGfm from 'remark-gfm';
import { getDocumentText } from '../api/documents';
import { documentKeys } from '../api/queryKeys';
import { copy } from '../copy/es';
import { BodyError, BodyLoading, BodyNotice } from './BodyState';
import { rehypeHighlightTerms } from './rehypeHighlightTerms';

const SECTION_HEADINGS = 'h1[id], h2[id], h3[id]';

const markdownClassName = [
  'flex flex-col gap-3 text-slate-800',
  '[&_h1]:text-2xl [&_h1]:font-bold [&_h1]:text-slate-900',
  '[&_h2]:text-xl [&_h2]:font-semibold [&_h2]:text-slate-900',
  '[&_h3]:text-lg [&_h3]:font-semibold [&_h3]:text-slate-900',
  '[&_ul]:list-disc [&_ul]:pl-6 [&_ol]:list-decimal [&_ol]:pl-6',
  '[&_a]:text-sky-800 [&_a]:underline',
  '[&_code]:rounded [&_code]:bg-slate-100 [&_code]:px-1 [&_code]:text-sm',
  '[&_pre]:overflow-x-auto [&_pre]:rounded-md [&_pre]:bg-slate-900 [&_pre]:p-3',
  '[&_pre_code]:bg-transparent [&_pre_code]:text-slate-100',
  '[&_blockquote]:border-l-4 [&_blockquote]:border-slate-300 [&_blockquote]:pl-3',
  '[&_table]:w-full [&_table]:text-sm [&_th]:border [&_th]:border-slate-200 [&_th]:px-2 [&_th]:py-1',
  '[&_td]:border [&_td]:border-slate-200 [&_td]:px-2 [&_td]:py-1',
  '[&_mark]:bg-amber-200',
].join(' ');

export type MarkdownBodyProps = {
  readonly documentId: string;
  readonly terms: readonly string[];
};

/**
 * Markdown is read from the stored file rather than from the indexed chunks: indexing strips the
 * markup to plain text, so the source is the only copy that still carries the structure the reader
 * came for.
 */
export function MarkdownBody({ documentId, terms }: MarkdownBodyProps) {
  const source = useQuery({
    queryKey: documentKeys.file(documentId),
    queryFn: ({ signal }) => getDocumentText(documentId, signal),
  });

  if (source.isPending) {
    return <BodyLoading />;
  }
  if (source.isError) {
    return <BodyError error={source.error} onRetry={() => void source.refetch()} />;
  }
  if (source.data.trim().length === 0) {
    return <BodyNotice text={copy.viewer.body.empty} />;
  }

  return <MarkdownArticle markdown={source.data} terms={terms} />;
}

type Section = { readonly id: string; readonly text: string; readonly level: number };

function MarkdownArticle({
  markdown,
  terms,
}: {
  readonly markdown: string;
  readonly terms: readonly string[];
}) {
  const bodyRef = useRef<HTMLDivElement>(null);
  const [sections, setSections] = useState<readonly Section[]>([]);

  // The table of contents is read back from what was rendered, so its links always point at the
  // ids rehype-slug actually produced instead of at ids a second slug implementation guessed.
  useEffect(() => {
    const body = bodyRef.current;
    if (!body) {
      return;
    }
    setSections(
      [...body.querySelectorAll(SECTION_HEADINGS)].map((heading) => ({
        id: heading.id,
        text: heading.textContent ?? '',
        level: Number(heading.tagName.slice(1)),
      })),
    );
  }, [markdown]);

  useEffect(() => {
    if (terms.length === 0) {
      return;
    }
    // Optional: jsdom has no layout, and a viewer that throws on a missing scroll is worse than
    // one that simply does not move.
    bodyRef.current?.querySelector('mark')?.scrollIntoView?.({ block: 'center' });
  }, [markdown, terms]);

  return (
    <div className="flex flex-col gap-6 lg:flex-row-reverse lg:items-start">
      <nav aria-label={copy.viewer.toc.title} className="lg:sticky lg:top-4 lg:w-64 lg:shrink-0">
        <h4 className="text-sm font-semibold text-slate-900">{copy.viewer.toc.title}</h4>
        {sections.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">{copy.viewer.toc.empty}</p>
        ) : (
          <ol className="mt-2 flex flex-col gap-1 text-sm">
            {sections.map((section) => (
              <li key={section.id} style={{ paddingLeft: `${(section.level - 1) * 0.75}rem` }}>
                <a href={`#${section.id}`} className="text-sky-800 underline">
                  {section.text}
                </a>
              </li>
            ))}
          </ol>
        )}
      </nav>

      <div ref={bodyRef} className={`min-w-0 flex-1 ${markdownClassName}`}>
        <Markdown
          remarkPlugins={[remarkGfm]}
          rehypePlugins={[rehypeSlug, [rehypeHighlightTerms, { terms }]]}
        >
          {markdown}
        </Markdown>
      </div>
    </div>
  );
}
