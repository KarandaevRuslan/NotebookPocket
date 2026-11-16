package dev.pocket.notebook.domain

/** Aggregate port for application composition; individual features may accept narrower ports. */
interface NotebookGateway :
    NotebookDocuments,
    LibraryCatalogAccess,
    NotebookSecurity,
    NotebookResourceAccess,
    NotebookTransfer,
    NotebookMaintenance
