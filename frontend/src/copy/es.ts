/**
 * Every user-facing string lives here. Components reference keys so the Spanish copy the
 * evaluators read can change without touching component code.
 *
 * The status, category, error-code and upload-rule maps are keyed by the wire values the API
 * contract fixes, which is what lets the presentation layer prove at compile time that every
 * value the backend can send has copy.
 */
export const copy = {
  locale: 'es-CO',
  appTitle: 'Buscador y Visor de Documentos Técnicos',
  appTagline: 'Carga, indexa y busca tu documentación técnica.',

  nav: {
    label: 'Navegación principal',
    search: 'Búsqueda',
    upload: 'Cargar',
    documents: 'Documentos',
  },

  units: {
    bytes: 'B',
    kilobytes: 'KB',
    megabytes: 'MB',
  },

  actions: {
    retry: 'Reintentar',
    previousPage: 'Anterior',
    nextPage: 'Siguiente',
    remove: 'Quitar',
    removeAll: 'Quitar todos',
    applyToAll: 'Aplicar a todos',
    submitUpload: 'Cargar documentos',
    submittingUpload: 'Cargando…',
    goToDocuments: 'Ver documentos',
    goToUpload: 'Cargar documentos',
    goToSearch: 'Ir a la búsqueda',
  },

  states: {
    loading: 'Cargando…',
    errorTitle: 'No se pudo completar la operación',
    unknownError: 'Ocurrió un error inesperado. Vuelve a intentarlo.',
    networkError: 'No se pudo contactar al servidor. Revisa tu conexión.',
    serviceUnavailable: 'El servicio no está disponible en este momento. Vuelve a intentarlo.',
    notFound: 'El recurso solicitado no existe.',
    payloadTooLarge: 'El envío supera el tamaño máximo que acepta el servidor.',
    invalidRequest: 'El servidor rechazó la solicitud porque algún dato no es válido.',
  },

  statuses: {
    PROCESANDO: 'Procesando',
    INDEXADO: 'Indexado',
    ERROR: 'Error',
  },

  statusDescriptions: {
    PROCESANDO: 'El documento se está procesando e indexando.',
    INDEXADO: 'El documento quedó indexado y ya se puede buscar.',
    ERROR: 'El documento no se pudo indexar.',
  },

  categories: {
    MANUAL: 'Manual',
    SPECIFICATION: 'Especificación',
    ARCHITECTURE_GUIDE: 'Guía de arquitectura',
    OTHER: 'Otro',
  },

  errorCodes: {
    PDF_NO_TEXT_LAYER: 'El PDF no tiene capa de texto, así que no hay nada que indexar.',
    UNSUPPORTED_FORMAT: 'El formato del archivo no está soportado.',
    CORRUPT_FILE: 'El archivo está dañado y no se pudo leer.',
    EMPTY_CONTENT: 'El archivo no contiene texto indexable.',
    PROCESSING_FAILED: 'El procesamiento falló por un error inesperado.',
  },
  unknownErrorCode: 'El documento no se pudo indexar.',

  uploadRules: {
    EXTENSION_ALLOWLIST: 'Formato no permitido. Solo se aceptan .txt, .md, .markdown y .pdf.',
    NON_EMPTY_FILE: 'El archivo está vacío.',
    MAXIMUM_FILE_SIZE: 'El archivo supera el tamaño máximo permitido.',
    PDF_HEADER: 'El archivo no es un PDF válido.',
    TEXT_WITHOUT_BINARY_BYTES: 'El archivo no es texto: contiene bytes binarios.',
    UNIQUE_CHECKSUM: 'Este contenido ya está cargado.',
  },
  unknownUploadRule: 'El servidor rechazó el archivo.',

  search: {
    title: 'Búsqueda de documentos',
    intro:
      'Busca por palabras o frases entre comillas sobre el título, los metadatos y el contenido de los documentos indexados.',
    pending: 'La búsqueda con resaltado y paginación llega en la siguiente entrega.',
  },

  upload: {
    title: 'Cargar documentos',
    intro:
      'Arrastra tus archivos, completa los metadatos y envíalos. La respuesta es inmediata: la indexación continúa en segundo plano y el estado llega en tiempo real.',
    dropzone: {
      inputLabel: 'Seleccionar archivos para cargar',
      idle: 'Arrastra los archivos aquí o pulsa para seleccionarlos.',
      active: 'Suelta los archivos para agregarlos.',
      hint: 'Formatos aceptados: .txt, .md, .markdown y .pdf. Hasta {maxFiles} archivos de {maxSize} cada uno.',
    },
    rejectedTitle: 'Archivos descartados',
    rejections: {
      extension: 'Formato no permitido. Solo se aceptan .txt, .md, .markdown y .pdf.',
      tooLarge: 'El archivo supera el tamaño máximo de {maxSize}.',
      empty: 'El archivo está vacío.',
      tooMany: 'Solo se pueden cargar {maxFiles} archivos por envío.',
      unknown: 'El archivo no se pudo agregar.',
      duplicateSelection: 'Ese archivo ya está en la lista.',
    },
    defaults: {
      legend: 'Valores comunes',
      hint: 'Completa estos campos y aplícalos a todas las filas de una vez.',
    },
    fields: {
      filename: 'Archivo',
      size: 'Tamaño',
      title: 'Título',
      author: 'Autor',
      category: 'Categoría',
      version: 'Versión',
      tags: 'Etiquetas',
      tagsHint: 'Separadas por comas',
      actions: 'Acciones',
    },
    table: {
      caption: 'Metadatos de los archivos por cargar',
      empty: 'Todavía no has agregado archivos.',
    },
    validation: {
      titleRequired: 'El título es obligatorio.',
      titleTooLong: 'El título admite hasta {max} caracteres.',
      authorRequired: 'El autor es obligatorio.',
      authorTooLong: 'El autor admite hasta {max} caracteres.',
      categoryRequired: 'La categoría es obligatoria.',
      versionRequired: 'La versión es obligatoria.',
      versionTooLong: 'La versión admite hasta {max} caracteres.',
      tagTooLong: 'Cada etiqueta admite hasta {max} caracteres.',
      tooManyTags: 'Se admiten hasta {max} etiquetas.',
      formInvalid: 'Revisa los campos marcados antes de enviar.',
      noFiles: 'Agrega al menos un archivo.',
    },
    accepted: {
      title: 'Documentos aceptados',
      hint: 'El estado de cada documento se actualiza en tiempo real; no hace falta recargar.',
      viewLink: 'Abrir el documento',
    },
    rowErrorLabel: 'Error del servidor',
    duplicateLink: 'Ver el documento existente',
  },

  documents: {
    title: 'Documentos',
    intro: 'Todos los documentos cargados, con su estado de indexación al día.',
    table: {
      caption: 'Documentos cargados',
      title: 'Título',
      author: 'Autor',
      category: 'Categoría',
      version: 'Versión',
      tags: 'Etiquetas',
      size: 'Tamaño',
      created: 'Cargado',
      status: 'Estado',
    },
    filter: {
      label: 'Filtrar por estado',
      all: 'Todos los estados',
    },
    empty: 'Todavía no hay documentos cargados.',
    emptyFiltered: 'Ningún documento tiene ese estado.',
    total: '{total} documentos',
    totalOne: '1 documento',
    pageIndicator: 'Página {page} de {pages}',
    noTags: 'Sin etiquetas',
  },

  viewer: {
    title: 'Visor de documentos',
    pending:
      'El visor con metadatos, tabla de contenido y resaltado llega en la siguiente entrega.',
    documentId: 'Identificador: {id}',
  },

  notifications: {
    indexed: {
      title: 'Documento indexado',
      description: '«{title}» ya se puede buscar.',
    },
    failed: {
      title: 'No se pudo indexar el documento',
      description: '«{title}»: {reason}',
    },
    processing: {
      title: 'Documento en proceso',
      description: '«{title}» se está indexando.',
    },
    unknownDocument: 'Documento sin título',
    streamLost: 'Se perdió la conexión de tiempo real. Reintentando…',
    streamRestored: 'Conexión de tiempo real restablecida.',
    uploadAccepted: {
      title: 'Carga aceptada',
      description: '{count} archivos quedaron en proceso de indexación.',
      descriptionOne: 'El archivo quedó en proceso de indexación.',
    },
    uploadRejected: 'No se cargó ningún archivo. Revisa los errores marcados.',
  },

  notFound: {
    title: 'Página no encontrada',
    description: 'La dirección que abriste no existe.',
  },
} as const;

export type CopyKey = keyof typeof copy;
