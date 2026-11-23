from typing import Literal
from pydantic import BaseModel, Field, model_validator

PAPERS = {'A4': (210, 297), 'A5': (148, 210), 'A3': (297, 420),
          'Letter': (215.9, 279.4), 'Legal': (215.9, 355.6), 'B5': (176, 250)}


class Settings(BaseModel):
    mode: Literal['single', 'multiple'] = 'single'
    expected: int = Field(1, ge=1, le=100)
    paper: Literal['A4', 'A5', 'A3', 'Letter', 'Legal', 'B5', 'Custom'] = 'A4'
    width: float = Field(210, gt=0, le=2000)
    height: float = Field(297, gt=0, le=2000)
    units: Literal['mm', 'in'] = 'mm'
    orientation: Literal['portrait', 'landscape'] = 'portrait'
    appearance: Literal['color', 'grayscale', 'bw'] = 'color'
    strength: float = Field(.25, ge=0, le=.65)
    layout: Literal['fit', 'preserve'] = 'fit'
    margin: float = Field(8, ge=0, le=40)
    watermark: str = Field('', max_length=100)
    watermark_position: Literal['center', 'top', 'bottom'] = 'center'
    watermark_size: int = Field(24, ge=8, le=72)
    watermark_opacity: float = Field(.12, ge=0, le=.8)
    numbers: bool = False
    number_position: Literal['left', 'center', 'right'] = 'center'
    number_start: int = Field(1, ge=1, le=9999)
    export: Literal['searchable', 'pdf', 'jpg', 'png'] = 'searchable'
    quality: int = Field(92, ge=65, le=100)
    camera: str = Field('', max_length=300)
    auto_capture: bool = False
    live_feedback: bool = True
    naming: str = Field('{date}_{title}', max_length=120)
    date_source: Literal['scan', 'document'] = 'scan'
    missing_date: Literal['scan', 'omit'] = 'scan'
    destination: str = Field('', max_length=500)
    retain_originals: bool = False

    def dimensions(self):
        w, h = PAPERS.get(self.paper, (self.width, self.height))
        if self.paper == 'Custom' and self.units == 'in':
            w, h = w * 25.4, h * 25.4
        w, h = sorted((w, h))
        if self.orientation == 'landscape':
            w, h = h, w
        return w, h

    @model_validator(mode='after')
    def usable_paper(self):
        w, h = self.dimensions()
        if min(w, h) < 20 or max(w, h) > 2000 or self.margin * 2 >= min(w, h):
            raise ValueError('Paper must be 20–2000 mm with room for margins.')
        if any(t not in ('date', 'title') for t in __import__('re').findall(r'\{([^}]+)\}', self.naming)):
            raise ValueError('Naming supports {date} and {title}.')
        return self


class Correction(BaseModel):
    revision: int
    rotation: Literal[0, 90, 180, 270] = 0
    crop: list[list[float]] | None = None

    @model_validator(mode='after')
    def valid_crop(self):
        if self.crop is not None:
            if len(self.crop) != 4 or any(len(p) != 2 or any(v < 0 or v > 1 for v in p) for p in self.crop):
                raise ValueError('Four normalized corners required.')
            import cv2
            import numpy as np
            points = np.array(self.crop, dtype=np.float32)
            if not cv2.isContourConvex(points) or abs(cv2.contourArea(points)) < .015:
                raise ValueError('Crop corners must form a convex page in clockwise order.')
        return self


class Approval(BaseModel):
    revision: int
    title: str = Field(min_length=1, max_length=200)
    document_date: str = Field('', max_length=10)
    filename: str = Field(min_length=1, max_length=150)
    destination: str = Field(min_length=1, max_length=500)

    @model_validator(mode='after')
    def date_valid(self):
        if not self.title.strip():
            raise ValueError('Title cannot be blank.')
        if self.document_date:
            from datetime import date
            date.fromisoformat(self.document_date)
        return self
