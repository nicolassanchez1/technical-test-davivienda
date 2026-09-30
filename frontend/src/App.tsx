import { Route, Routes } from 'react-router';
import { DocumentsPage } from './documents/DocumentsPage';
import { SearchPage } from './search/SearchPage';
import { AppLayout } from './shell/AppLayout';
import { NotFoundPage } from './shell/NotFoundPage';
import { UploadPage } from './upload/UploadPage';
import { DocumentViewerPage } from './viewer/DocumentViewerPage';

export function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<SearchPage />} />
        <Route path="upload" element={<UploadPage />} />
        <Route path="documents" element={<DocumentsPage />} />
        <Route path="documents/:id" element={<DocumentViewerPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
