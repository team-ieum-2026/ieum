import type { Sentence } from "@/types/resume";

/** JSON의 문장 순서와 문단 번호를 유지한다. 번호가 없으면 앞 문단에 이어 붙인다. */
export function groupCoverLetterParagraphs(sentences: Sentence[]): Sentence[][] {
  const paragraphs: Sentence[][] = [];
  let current: Sentence[] = [];
  let paragraph: number | null = null;
  for (const sentence of sentences) {
    if (current.length && paragraph !== null && sentence.paragraph != null && sentence.paragraph !== paragraph) {
      paragraphs.push(current);
      current = [];
    }
    current.push(sentence);
    if (sentence.paragraph != null) paragraph = sentence.paragraph;
  }
  if (current.length) paragraphs.push(current);
  return paragraphs;
}
